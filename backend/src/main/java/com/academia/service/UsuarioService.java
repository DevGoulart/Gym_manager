package com.academia.service;

import com.academia.dto.AuthDtos.UsuarioRequest;
import com.academia.dto.AuthDtos.UsuarioResponse;
import com.academia.exception.RecursoNaoEncontradoException;
import com.academia.exception.RegraNegocioException;
import com.academia.model.Perfil;
import com.academia.model.Usuario;
import com.academia.repository.SessaoRepository;
import com.academia.repository.UsuarioRepository;
import com.academia.security.AcademiaAtual;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Gestão dos usuários da própria academia (somente administradores chegam aqui).
 * O dono de uma rede não aparece nem pode ser alterado por aqui: ele é gerenciado acima das unidades.
 */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final SessaoRepository sessaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final AcademiaAtual academiaAtual;
    private final DispositivosConfiaveis dispositivos;
    private final AssinaturaService assinaturas;
    private final Notificador notificador;

    public UsuarioService(UsuarioRepository usuarioRepository, SessaoRepository sessaoRepository,
                          PasswordEncoder passwordEncoder, AcademiaAtual academiaAtual, DispositivosConfiaveis dispositivos, AssinaturaService assinaturas,
                          Notificador notificador) {
        this.notificador = notificador;
        this.assinaturas = assinaturas;
        this.usuarioRepository = usuarioRepository;
        this.sessaoRepository = sessaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.academiaAtual = academiaAtual;
        this.dispositivos = dispositivos;
    }

    public List<UsuarioResponse> listar() {
        return usuarioRepository.findByAcademiaIdAndPerfilNotOrderByNomeAsc(academiaAtual.id(), Perfil.DONO).stream()
                .map(UsuarioResponse::de).toList();
    }

    private Usuario buscar(Long id) {
        return usuarioRepository.findByIdAndAcademiaIdAndPerfilNot(id, academiaAtual.id(), Perfil.DONO)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário " + id + " não encontrado"));
    }

    /** Dentro de uma unidade só existem administradores e funcionários; dono de rede não se cria por aqui. */
    private static void validarPerfil(Perfil perfil) {
        if (perfil == Perfil.DONO) {
            throw new RegraNegocioException("O perfil de dono da rede não pode ser atribuído a usuários da unidade.");
        }
    }

    @Transactional
    public UsuarioResponse criar(UsuarioRequest req) {
        validarPerfil(req.perfil());
        if (req.senha() == null || req.senha().isBlank()) {
            throw new RegraNegocioException("Informe uma senha com pelo menos 8 caracteres.");
        }
        String email = AuthService.normalizarEmail(req.email());
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new RegraNegocioException("Já existe uma conta com o e-mail " + email);
        }
        assinaturas.exigirVagaUsuario(academiaAtual.id()); // limite de usuários do plano
        Usuario u = new Usuario();
        u.setAcademia(academiaAtual.referencia());
        u.setNome(req.nome().trim());
        u.setEmail(email);
        u.setPerfil(req.perfil());
        u.setAtivo(req.ativo() == null || req.ativo());
        u.setSenhaHash(passwordEncoder.encode(req.senha()));
        return UsuarioResponse.de(usuarioRepository.save(u));
    }

    @Transactional
    public UsuarioResponse atualizar(Long id, UsuarioRequest req) {
        validarPerfil(req.perfil());
        Usuario u = buscar(id);
        String email = AuthService.normalizarEmail(req.email());
        if (usuarioRepository.existsByEmailIgnoreCaseAndIdNot(email, id)) {
            throw new RegraNegocioException("Já existe uma conta com o e-mail " + email);
        }
        boolean ativo = req.ativo() == null || req.ativo();
        boolean ehVoce = u.getId().equals(academiaAtual.usuario().id());
        if (ehVoce && (!ativo || req.perfil() != Perfil.ADMIN)) {
            throw new RegraNegocioException("Você não pode desativar nem tirar o perfil de administrador da sua própria conta.");
        }
        boolean deixaDeSerAdminAtivo = u.getPerfil() == Perfil.ADMIN && u.isAtivo() && (!ativo || req.perfil() != Perfil.ADMIN);
        if (deixaDeSerAdminAtivo) garantirOutroAdmin();

        u.setNome(req.nome().trim());
        u.setEmail(email);
        u.setPerfil(req.perfil());
        u.setAtivo(ativo);
        boolean trocouSenha = req.senha() != null && !req.senha().isBlank();
        if (trocouSenha) u.setSenhaHash(passwordEncoder.encode(req.senha()));
        usuarioRepository.save(u);
        // Senha trocada ou usuário desativado: as sessões abertas dele deixam de valer
        if (!ehVoce && (trocouSenha || !ativo)) sessaoRepository.apagarDoUsuario(u.getId());
        // Senha trocada: os navegadores lembrados dele voltam a pedir o código das duas etapas
        if (trocouSenha) dispositivos.esquecerTodos(u.getId());
        if (trocouSenha && !ehVoce) notificador.senhaAlterada(u.getId(), academiaAtual.usuario().nome());
        return UsuarioResponse.de(u);
    }

    @Transactional
    public void excluir(Long id) {
        Usuario u = buscar(id);
        if (u.getId().equals(academiaAtual.usuario().id())) {
            throw new RegraNegocioException("Você não pode excluir a sua própria conta.");
        }
        if (u.getPerfil() == Perfil.ADMIN && u.isAtivo()) garantirOutroAdmin();
        sessaoRepository.apagarDoUsuario(u.getId());
        dispositivos.esquecerTodos(u.getId());
        notificador.apagarDoUsuario(u.getId());
        usuarioRepository.delete(u);
    }

    /**
     * Para quem perdeu o celular e os códigos de recuperação: desliga a verificação em duas etapas
     * e encerra as sessões abertas dessa pessoa. Ela entra só com a senha e ativa de novo.
     */
    @Transactional
    public UsuarioResponse redefinirDoisFatores(Long id) {
        Usuario u = buscar(id);
        if (u.getId().equals(academiaAtual.usuario().id())) {
            throw new RegraNegocioException("Para a sua própria conta, use \"Verificação em duas etapas\" no menu do usuário.");
        }
        if (!u.doisFatoresAtivo()) {
            throw new RegraNegocioException("Este usuário não usa verificação em duas etapas.");
        }
        DoisFatoresService.limpar(u);
        usuarioRepository.save(u);
        sessaoRepository.apagarDoUsuario(u.getId());
        dispositivos.esquecerTodos(u.getId());
        notificador.doisFatoresDesativada(u.getId(), academiaAtual.usuario().nome());
        return UsuarioResponse.de(u);
    }

    /** A academia nunca pode ficar sem nenhum administrador ativo (o dono da rede, se for desta unidade, também conta). */
    private void garantirOutroAdmin() {
        Long id = academiaAtual.id();
        long admins = usuarioRepository.countByAcademiaIdAndPerfilAndAtivoTrue(id, Perfil.ADMIN)
                + usuarioRepository.countByAcademiaIdAndPerfilAndAtivoTrue(id, Perfil.DONO);
        if (admins <= 1) {
            throw new RegraNegocioException("A academia precisa de pelo menos um administrador ativo.");
        }
    }
}

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

/** Gestão dos usuários da própria academia (somente administradores chegam aqui). */
@Service
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final SessaoRepository sessaoRepository;
    private final PasswordEncoder passwordEncoder;
    private final AcademiaAtual academiaAtual;

    public UsuarioService(UsuarioRepository usuarioRepository, SessaoRepository sessaoRepository,
                          PasswordEncoder passwordEncoder, AcademiaAtual academiaAtual) {
        this.usuarioRepository = usuarioRepository;
        this.sessaoRepository = sessaoRepository;
        this.passwordEncoder = passwordEncoder;
        this.academiaAtual = academiaAtual;
    }

    public List<UsuarioResponse> listar() {
        return usuarioRepository.findByAcademiaIdOrderByNomeAsc(academiaAtual.id()).stream()
                .map(UsuarioResponse::de).toList();
    }

    private Usuario buscar(Long id) {
        return usuarioRepository.findByIdAndAcademiaId(id, academiaAtual.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Usuário " + id + " não encontrado"));
    }

    @Transactional
    public UsuarioResponse criar(UsuarioRequest req) {
        if (req.senha() == null || req.senha().isBlank()) {
            throw new RegraNegocioException("Informe uma senha com pelo menos 8 caracteres.");
        }
        String email = AuthService.normalizarEmail(req.email());
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new RegraNegocioException("Já existe uma conta com o e-mail " + email);
        }
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
        return UsuarioResponse.de(u);
    }

    /** A academia nunca pode ficar sem nenhum administrador ativo. */
    private void garantirOutroAdmin() {
        if (usuarioRepository.countByAcademiaIdAndPerfilAndAtivoTrue(academiaAtual.id(), Perfil.ADMIN) <= 1) {
            throw new RegraNegocioException("A academia precisa de pelo menos um administrador ativo.");
        }
    }
}

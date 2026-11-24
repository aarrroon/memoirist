package com.memoirist.template;

import com.memoirist.common.ConflictException;
import com.memoirist.common.CreateResult;
import com.memoirist.common.NotFoundException;
import com.memoirist.template.dto.TemplateCreateRequest;
import com.memoirist.template.dto.TemplateResponse;
import com.memoirist.template.dto.TemplateUpdateRequest;
import com.memoirist.user.CurrentUserProvider;
import com.memoirist.user.User;
import com.memoirist.user.UserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The user's templates (API_CONTRACT, Templates; D-06). A template is a name and a list of prompts. Entries remember
 * which template they started from ({@code entries.template_id}) but never copy anything from it, so editing a
 * template changes nothing that is already written, and deleting one only empties that column: the database does it
 * ({@code ON DELETE SET NULL}), so this service never touches an entry.
 *
 * <p>Every lookup is scoped to the current user, and a template that is someone else's is {@code 404}, the same as one
 * that doesn't exist.
 */
@Service
@Transactional
public class TemplateService {

    private final TemplateRepository templates;
    private final UserService users;
    private final CurrentUserProvider currentUser;

    public TemplateService(TemplateRepository templates, UserService users, CurrentUserProvider currentUser) {
        this.templates = templates;
        this.users = users;
        this.currentUser = currentUser;
    }

    /**
     * Creates a template, or answers with the stored one when the user already has a template with the client's
     * {@code id} (a safe retry, D-12: the body is not applied again).
     *
     * @throws NotFoundException if the request names a user who isn't there (the first thing every create checks)
     * @throws ConflictException if the {@code id} belongs to another user's template
     */
    public CreateResult<TemplateResponse> create(TemplateCreateRequest request) {
        User user = users.currentUser();
        UUID id = request.id();
        if (id == null) {
            id = UUID.randomUUID();
        } else {
            Optional<Template> own = templates.findByIdAndUserId(id, user.getId());
            if (own.isPresent()) {
                return CreateResult.replayed(TemplateMapper.toResponse(own.get()));
            }
            if (templates.existsById(id)) {
                // Someone else has it. Say only that it is taken, never whose it is or what it holds.
                throw new ConflictException("That id is already in use.");
            }
        }
        Template template = new Template(id, user.getId(), request.name(), request.subheadings());
        return CreateResult.created(TemplateMapper.toResponse(templates.saveAndFlush(template)));
    }

    /** All of the user's templates, by name (see {@link Template#BY_NAME}). Empty for a user with none. */
    @Transactional(readOnly = true)
    public List<TemplateResponse> list() {
        return templates.findByUserId(currentUser.currentUserId()).stream()
                .sorted(Template.BY_NAME)
                .map(TemplateMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TemplateResponse get(UUID templateId) {
        return TemplateMapper.toResponse(find(templateId));
    }

    /**
     * Replaces the template's name and subheadings with the request's (a {@code PUT}). The template is a managed
     * entity, so Hibernate writes one {@code UPDATE}, or none if nothing changed; the flush is
     * explicit so that {@code updatedAt} in the response is the new one. No entry is read or written.
     */
    public TemplateResponse update(UUID templateId, TemplateUpdateRequest request) {
        Template template = find(templateId);
        template.setName(request.name());
        template.setSubheadings(request.subheadings());
        return TemplateMapper.toResponse(templates.saveAndFlush(template));
    }

    /**
     * Deletes the template and only the template. The entries that started from it keep everything they hold, sealed
     * ones and ones in Recently deleted included, and their {@code template_id} becomes {@code null} (the foreign key's
     * {@code ON DELETE SET NULL}).
     */
    public void delete(UUID templateId) {
        templates.delete(find(templateId));
    }

    private Template find(UUID templateId) {
        return templates
                .findByIdAndUserId(templateId, currentUser.currentUserId())
                .orElseThrow(() -> new NotFoundException("Template"));
    }
}

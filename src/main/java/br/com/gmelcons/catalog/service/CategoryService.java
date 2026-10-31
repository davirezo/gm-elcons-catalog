package br.com.gmelcons.catalog.service;

import br.com.gmelcons.catalog.domain.AuditOperation;
import br.com.gmelcons.catalog.dto.Dtos.*;
import br.com.gmelcons.catalog.entity.AttributeDefinition;
import br.com.gmelcons.catalog.entity.Category;
import br.com.gmelcons.catalog.exception.BusinessException;
import br.com.gmelcons.catalog.exception.NotFoundException;
import br.com.gmelcons.catalog.repository.AttributeDefinitionRepository;
import br.com.gmelcons.catalog.repository.CategoryRepository;
import br.com.gmelcons.catalog.repository.ProductAttributeValueRepository;
import br.com.gmelcons.catalog.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static br.com.gmelcons.catalog.service.AuditService.fields;
import static br.com.gmelcons.catalog.service.Text.blankToNull;

/** Leitura liberada a qualquer usuário autenticado; escrita somente ADMIN. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categories;
    private final AttributeDefinitionRepository definitions;
    private final ProductRepository products;
    private final ProductAttributeValueRepository values;
    private final AuditService audit;

    public List<CategoryDto> list() {
        return categories.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CategoryDto create(CategoryRequest req) {
        String name = req.name().trim();
        if (categories.existsByNameIgnoreCase(name)) {
            throw BusinessException.conflict("Já existe uma categoria com este nome");
        }
        Category category = new Category();
        category.setName(name);
        category.setDescription(blankToNull(req.description()));
        categories.save(category);
        audit.log(AuditOperation.CATEGORY_CREATE, "Category", category.getId(), null, snapshot(category));
        return toDto(category);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public CategoryDto update(Long id, CategoryRequest req) {
        Category category = findCategory(id);
        String name = req.name().trim();
        if (categories.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw BusinessException.conflict("Já existe uma categoria com este nome");
        }
        Map<String, Object> before = snapshot(category);
        category.setName(name);
        category.setDescription(blankToNull(req.description()));
        audit.log(AuditOperation.CATEGORY_UPDATE, "Category", id, before, snapshot(category));
        return toDto(category);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(Long id) {
        Category category = findCategory(id);
        if (products.countByCategoryId(id) > 0) {
            throw BusinessException.conflict("A categoria possui produtos cadastrados e não pode ser excluída");
        }
        Map<String, Object> before = snapshot(category);
        definitions.deleteAll(definitions.findByCategoryIdOrderByDisplayOrderAscIdAsc(id));
        categories.delete(category);
        audit.log(AuditOperation.CATEGORY_DELETE, "Category", id, before, null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AttributeDefinitionDto addAttribute(Long categoryId, AttributeDefinitionRequest req) {
        Category category = findCategory(categoryId);
        String name = req.name().trim();
        if (definitions.existsByCategoryIdAndNameIgnoreCase(categoryId, name)) {
            throw BusinessException.conflict("Já existe um atributo com este nome na categoria");
        }
        AttributeDefinition def = new AttributeDefinition();
        def.setCategory(category);
        def.setName(name);
        def.setDataType(req.dataType());
        def.setRequired(req.required());
        def.setUnit(blankToNull(req.unit()));
        def.setDisplayOrder(req.displayOrder() != null ? req.displayOrder()
                : (int) definitions.countByCategoryId(categoryId) + 1);
        definitions.save(def);
        audit.log(AuditOperation.ATTRIBUTE_DEFINITION_CREATE, "AttributeDefinition", def.getId(), null, snapshot(def));
        return toDto(def);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AttributeDefinitionDto updateAttribute(Long id, AttributeDefinitionRequest req) {
        AttributeDefinition def = findDefinition(id);
        String name = req.name().trim();
        if (definitions.existsByCategoryIdAndNameIgnoreCaseAndIdNot(def.getCategory().getId(), name, id)) {
            throw BusinessException.conflict("Já existe um atributo com este nome na categoria");
        }
        if (def.getDataType() != req.dataType() && values.countByDefinitionId(id) > 0) {
            throw BusinessException.conflict("Não é possível alterar o tipo de um atributo que já possui valores preenchidos");
        }
        Map<String, Object> before = snapshot(def);
        def.setName(name);
        def.setDataType(req.dataType());
        def.setRequired(req.required());
        def.setUnit(blankToNull(req.unit()));
        if (req.displayOrder() != null) {
            def.setDisplayOrder(req.displayOrder());
        }
        audit.log(AuditOperation.ATTRIBUTE_DEFINITION_UPDATE, "AttributeDefinition", id, before, snapshot(def));
        return toDto(def);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void deleteAttribute(Long id) {
        AttributeDefinition def = findDefinition(id);
        if (values.countByDefinitionId(id) > 0) {
            throw BusinessException.conflict("O atributo possui valores preenchidos em produtos e não pode ser excluído");
        }
        Map<String, Object> before = snapshot(def);
        definitions.delete(def);
        audit.log(AuditOperation.ATTRIBUTE_DEFINITION_DELETE, "AttributeDefinition", id, before, null);
    }

    private Category findCategory(Long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Categoria não encontrada"));
    }

    private AttributeDefinition findDefinition(Long id) {
        return definitions.findById(id).orElseThrow(() -> new NotFoundException("Atributo não encontrado"));
    }

    private CategoryDto toDto(Category c) {
        List<AttributeDefinitionDto> attrs = definitions.findByCategoryIdOrderByDisplayOrderAscIdAsc(c.getId())
                .stream().map(this::toDto).toList();
        return new CategoryDto(c.getId(), c.getName(), c.getDescription(), products.countByCategoryId(c.getId()), attrs);
    }

    private AttributeDefinitionDto toDto(AttributeDefinition d) {
        return new AttributeDefinitionDto(d.getId(), d.getCategory().getId(), d.getName(), d.getDataType(),
                d.isRequired(), d.getUnit(), d.getDisplayOrder());
    }

    private static Map<String, Object> snapshot(Category c) {
        return fields("name", c.getName(), "description", c.getDescription());
    }

    private static Map<String, Object> snapshot(AttributeDefinition d) {
        return fields("category", d.getCategory().getName(), "name", d.getName(), "dataType", d.getDataType(),
                "required", d.isRequired(), "unit", d.getUnit(), "displayOrder", d.getDisplayOrder());
    }
}

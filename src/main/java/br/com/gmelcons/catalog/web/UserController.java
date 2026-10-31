package br.com.gmelcons.catalog.web;

import br.com.gmelcons.catalog.dto.Dtos.UserCreateRequest;
import br.com.gmelcons.catalog.dto.Dtos.UserDto;
import br.com.gmelcons.catalog.dto.Dtos.UserUpdateRequest;
import br.com.gmelcons.catalog.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Usuários (ADMIN)")
public class UserController {

    private final UserService service;

    @GetMapping
    public List<UserDto> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto create(@Valid @RequestBody UserCreateRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable Long id, @Valid @RequestBody UserUpdateRequest req) {
        return service.update(id, req);
    }
}

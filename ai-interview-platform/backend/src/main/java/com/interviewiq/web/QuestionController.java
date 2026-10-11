package com.interviewiq.web;

import com.interviewiq.service.QuestionService;
import com.interviewiq.web.dto.QuestionRequest;
import com.interviewiq.web.dto.QuestionResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionService service;

    public QuestionController(QuestionService service) {
        this.service = service;
    }

    @GetMapping
    public List<QuestionResponse> search(@RequestParam(required = false) String role,
                                         @RequestParam(required = false) String difficulty,
                                         @RequestParam(required = false) String search) {
        return service.search(role, difficulty, search);
    }

    @GetMapping("/roles")
    public List<String> roles() {
        return service.roles();
    }

    @GetMapping("/topics")
    public List<String> topics() {
        return service.topics();
    }

    /** Skill tags used for tailoring questions to a student's profile. */
    @GetMapping("/skills")
    public List<String> skills() {
        return service.skills();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionResponse create(@Valid @RequestBody QuestionRequest request) {
        return service.create(request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}

package com.interviewiq.repo;

import com.interviewiq.domain.Difficulty;
import com.interviewiq.domain.Question;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByRoleAndDifficulty(String role, Difficulty difficulty);

    List<Question> findByRole(String role);

    long countByRole(String role);

    @Query("""
            select q from Question q
            where (:role is null or lower(q.role) = lower(:role))
              and (:difficulty is null or q.difficulty = :difficulty)
              and (:search is null or lower(q.text) like lower(concat('%', :search, '%'))
                                 or lower(q.topic) like lower(concat('%', :search, '%')))
            order by q.id desc
            """)
    List<Question> search(@Param("role") String role,
                          @Param("difficulty") Difficulty difficulty,
                          @Param("search") String search);

    @Query("select distinct q.role from Question q order by q.role")
    List<String> findDistinctRoles();

    @Query("select distinct q.topic from Question q where q.topic is not null order by q.topic")
    List<String> findDistinctTopics();
}

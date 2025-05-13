// TestService.java
package com.example.TelegramTestBot.service;

import com.example.TelegramTestBot.model.*;
import com.example.TelegramTestBot.repository.TestRepository;
import com.example.TelegramTestBot.repository.TestResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TestService {

    private final TestRepository testRepository;
    private final QuestionService questionService;
    private final TestResultRepository testResultRepository;
    public TestService(TestRepository testRepository, QuestionService questionService, TestResultRepository testResultRepository) {
        this.testRepository = testRepository;
        this.questionService = questionService;
        this.testResultRepository = testResultRepository;
    }
    public String getUserResults(Long userId) {
        List<TestResult> testResults = testResultRepository.findByUserId(userId);

        StringBuilder resultBuilder = new StringBuilder();
        for (TestResult result : testResults) {
            // Защита от null значения score
            Integer score = result.getScore();
            Integer maxScore = result.getMaxScore();

            if (score != null && maxScore != null) {
                double percentage = (score * 100.0) / maxScore;
                resultBuilder.append(result.getTest().getTitle())
                        .append(" - Бал: ").append(score)
                        .append(" / ").append(maxScore)
                        .append(" - ").append(String.format("%.2f", percentage))
                        .append("%\n");
            } else {
                resultBuilder.append(result.getTest().getTitle())
                        .append(" - Не завершен.\n");
            }
        }

        return resultBuilder.length() > 0 ? resultBuilder.toString() : "❌ Нет данных о результатах тестов.";
    }
    @Transactional(readOnly = true)
    public List<UserResult> getUserResults(Test test) {

        // 1. Загружаем все результаты этого теста
        List<TestResult> allResults = testResultRepository.findByTest(test);

        Map<Long, UserResult> best = new HashMap<>();
        Long authorId = test.getCreator() != null ? test.getCreator().getId() : null;

        // 2. Группируем по пользователям, берём лучший процент
        for (TestResult tr : allResults) {
            User u = tr.getUser();           // гарантируем, что user загружен
            if (u == null) continue;

            // процент правильных ответов
            double perc = tr.getMaxScore() == null || tr.getMaxScore() == 0
                    ? 0.0
                    : tr.getScore() * 100.0 / tr.getMaxScore();

            // красивое имя: ФИО, иначе username
            String name = Optional.ofNullable(u.getFullName())
                    .filter(s -> !s.isBlank())
                    .orElse(u.getUsername());

            if (Objects.equals(u.getId(), authorId)) {
                name += " (автор)";
            }

            // сохраняем лучший результат пользователя
            UserResult currentBest = best.get(u.getId());
            if (currentBest == null || currentBest.getPercentage() < perc) {
                best.put(u.getId(), new UserResult(name, perc, u.getId()));
            }
        }

        return new ArrayList<>(best.values());
    }
    @Transactional
    public void recordTestResult(User user,
                                 Test test,
                                 int correctCount,
                                 int totalQuestions) {

        int maxScore = totalQuestions;
        int score    = correctCount;     // сколько баллов набрал

        TestResult tr = new TestResult();
        tr.setUser(user);
        tr.setTest(test);

        tr.setResult(score);             // ← 1) старое поле
        tr.setScore(score);              // ← 2) новое поле

        tr.setMaxScore(maxScore);
        tr.setCompletionDate(LocalDateTime.now());

        testResultRepository.save(tr);
    }

    @Transactional(readOnly = true)
    public List<Test> getTestsCreatedByUser(User creator) {
        return testRepository.findByCreator(creator);          // нужен метод в репо
    }
    @Transactional(readOnly = true)
    public long getQuestionCount(Test test) {
        // чтобы не гонять лишний запрос, если коллекция уже загружена —
        if (test.getQuestions() != null && !test.getQuestions().isEmpty()) {
            return test.getQuestions().size();
        }
        return questionService.getQuestionsByTestId(test.getId()).size();
    }
    @Transactional
    public void renameTest(Test test, String newTitle) {
        test.setTitle(newTitle);
        testRepository.save(test);
    }
    /** найти тест по названию и автору */
    @Transactional(readOnly = true)
    public Optional<Test> findByTitleAndUser(String title, User creator) {
        return testRepository.findByTitleIgnoreCaseAndCreator(title, creator);
    }

    /** «Удалить тест» (каскад/FK заведены в JPA-модели) */
    @Transactional
    public void deleteTest(Test test) {
        testRepository.delete(test);
    }

    /** краткая сводка: кто проходил тест (score – лучшее значение пользователя) */
    @Transactional(readOnly = true)
    public List<UserResult> getTestParticipants(Test test) {
        // получаем все результаты
        List<TestResult> results = testResultRepository.findByTest(test);
        Map<Long, UserResult> map = new HashMap<>();
        for (TestResult r : results) {
            long uid   = r.getUser().getId();
            int  score = r.getScore() != null ? r.getScore() : 0;
            int  best  = map.containsKey(uid) ? map.get(uid).getScore() : -1;
            if (score > best) {
                map.put(uid, new UserResult(r.getUser().getUsername(), score, uid));
            }
        }
        return new ArrayList<>(map.values());
    }

    /** все результаты конкретного пользователя по данному тесту */
    @Transactional(readOnly = true)
    public List<TestResult> getResultsByTestAndUser(Test test, User user) {
        return testResultRepository.findByTestAndUser(test, user);
    }
    public List<TestResult> getCompletedTestsForUser(User user) {
        // Получаем результаты тестов, которые прошел пользователь
        return testResultRepository.findByUser(user);
    }
    @Transactional(readOnly = true)
    public List<TestResult> getResultsByTest(Test test) {
        return testResultRepository.findByTest(test);
    }
    @Transactional(readOnly = true)
    public List<Test> getTestsByTitle(String title) {
        return testRepository.findByTitleIgnoreCase(title);
    }

    @Transactional
    public Test createNewTest(User creator, String testTitle) {
        Test test = new Test();
        test.setCreator(creator);
        test.setTitle(testTitle);
        test.setStatus(Test.TestStatus.DRAFT);
        return testRepository.save(test);
    }

    @Transactional(readOnly = true)
    public List<Question> getTestQuestionsByTitle(String title) {
        return testRepository.findByTitleIgnoreCase(title)
                .stream()
                .findFirst()
                .map(t -> questionService.getQuestionsByTestId(t.getId()))
                .orElse(List.of());
    }

    @Transactional(readOnly = true)
    public List<Test> getAvailableTests(User user) {
        return testRepository.findAll();
    }

    @Transactional
    public void addQuestionsToTest(Long testId, List<Question> questions) {
        Optional<Test> testOpt = testRepository.findById(testId);
        if (testOpt.isPresent()) {
            Test test = testOpt.get();
            questions.forEach(q -> {
                q.setTest(test);
                questionService.save(q);
            });
            test.setStatus(Test.TestStatus.PUBLISHED);
            testRepository.save(test);
        }
    }

    @Transactional(readOnly = true)
    public List<Question> getTestQuestions(Test test) {
        return questionService.getQuestionsWithAnswersByTest(test);
    }

    @Transactional
    public Test saveTest(Test test) {
        return testRepository.save(test);
    }

    @Transactional
    public void addQuestionToTest(Long testId, Question question) {
        testRepository.findById(testId).ifPresent(test -> {
            question.setTest(test);
            questionService.save(question);
            test.setStatus(Test.TestStatus.PUBLISHED);
            testRepository.save(test);
        });
    }

    @Transactional(readOnly = true)
    public Optional<Test> findById(Long testId) {
        return testRepository.findById(testId);
    }
}

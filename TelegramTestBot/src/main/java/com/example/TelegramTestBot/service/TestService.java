// TestService.java
package com.example.TelegramTestBot.service;

import com.example.TelegramTestBot.model.*;
import com.example.TelegramTestBot.repository.DetailedResultRepository;
import com.example.TelegramTestBot.repository.QuestionRepository;
import com.example.TelegramTestBot.repository.TestRepository;
import com.example.TelegramTestBot.repository.TestResultRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.IntStream;

@Service
public class TestService {
    private final DetailedResultRepository detailedResultRepository;
    private final TestRepository testRepository;
    private final QuestionRepository questionRepository;
    private final TestResultRepository testResultRepository;
    public TestService(DetailedResultRepository detailedResultRepository, TestRepository testRepository, QuestionRepository questionService, TestResultRepository testResultRepository) {
        this.detailedResultRepository = detailedResultRepository;
        this.testRepository = testRepository;
        this.questionRepository = questionService;
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
                                 List<Question> questions,
                                 List<Integer> userAnswers) {
        int score = IntStream.range(0, questions.size())
                .map(i -> {
                    Integer ua = userAnswers.get(i);
                    Integer ca = questions.get(i).getAnswerOptions().stream()
                            .filter(AnswerOption::getIsCorrect)
                            .map(AnswerOption::getOptionNumber)
                            .findFirst()
                            .orElse(null);        // правильный вариант
                    return (ua != null && ua.equals(ca)) ? 1 : 0;
                })
                .sum();
        TestResult tr = new TestResult();
        tr.setUser(user);
        tr.setTest(test);
        tr.setScore(score);
        tr.setMaxScore(questions.size());
        tr.setCompletionDate(LocalDateTime.now());
        testResultRepository.save(tr);
        for (int i = 0; i < questions.size(); i++) {
            Question q = questions.get(i);
            Integer ua = userAnswers.get(i);
            Integer ca = q.getAnswerOptions().stream()
                    .filter(AnswerOption::getIsCorrect)
                    .map(AnswerOption::getOptionNumber)
                    .findFirst()
                    .orElse(null);
            int pts = (ua != null && ua.equals(ca)) ? 1 : 0;

            DetailedResult dr = new DetailedResult();
            dr.setTestResult(tr);
            dr.setQuestion(q);
            dr.setQuestionIndex(i + 1);
            dr.setUserAnswer(ua);
            dr.setCorrectAnswer(ca);
            dr.setPoints(pts);
            detailedResultRepository.save(dr);
        }
    }
    @Transactional(readOnly = true)
    public List<Test> getTestsCreatedByUser(User creator) {
        return testRepository.findByCreator(creator);
    }
    @Transactional(readOnly = true)
    public long getQuestionCount(Test t) {
        return questionRepository.countByTest(t);
    }
    @Transactional
    public void renameTest(Test test, String newTitle) {
        test.setTitle(newTitle);
        testRepository.save(test);
    }
    @Transactional(readOnly = true)
    public List<DetailedResult> getDetailedResults(TestResult tr) {
        return detailedResultRepository.findByTestResultOrderByQuestionIndex(tr);
    }
    @Transactional(readOnly = true)
    public Optional<Test> findByTitleAndUser(String title, User creator) {
        return testRepository.findByTitleIgnoreCaseAndCreator(title, creator);
    }
    @Transactional
    public void save(Test test) {
        testRepository.save(test);
    }
    @Transactional
    public void deleteTest(Test test) {
        testRepository.delete(test);
    }
    @Transactional(readOnly = true)
    public List<TestResult> getResultsByTestAndUser(Test test, User user) {
        return testResultRepository.findByTestAndUser(test, user);
    }
    @Transactional(readOnly = true)
    public List<UserResult> getTestParticipants(Test test) {
        List<TestResult> results = testResultRepository.findByTest(test);
        Map<Long, UserResult> map = new HashMap<>();

        for (TestResult r : results) {
            User u = r.getUser();
            if (u == null) continue;

            long uid = u.getId();
            int  score = r.getScore() != null ? r.getScore() : 0;
            int  max   = r.getMaxScore() != null ? r.getMaxScore() : 0;
            String name = Optional.ofNullable(u.getFullName())
                    .filter(s -> !s.isBlank())
                    .orElse(u.getUsername());
            if (Objects.equals(uid, test.getCreator().getId())) {
                name += " (автор)";
            }
            UserResult ur = new UserResult(name, u.getUsername(), score * 100.0 / (max == 0 ? 1 : max));
            ur.setScore(score);
            ur.setMaxScore(max);
            ur.setUserId(uid);
            UserResult prev = map.get(uid);
            if (prev == null || ur.getScore() > prev.getScore()) {
                map.put(uid, ur);
            }
        }
        return new ArrayList<>(map.values());
    }
    @Transactional(readOnly = true)
    public List<Test> getAvailableTests(User user) {
        return testRepository.findAll();
    }
}

package com.example.TelegramTestBot.service;

import com.example.TelegramTestBot.model.Question;
import com.example.TelegramTestBot.model.Test;
import com.example.TelegramTestBot.repository.QuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuestionService {

    private final QuestionRepository questionRepository;

    public QuestionService(QuestionRepository questionRepository) {
        this.questionRepository = questionRepository;
    }

    @Transactional
    public Question save(Question question) {
        return questionRepository.save(question);
    }
    @Transactional
    public Question addQuestion(Test test, String text) {
        Question q = new Question();
        q.setTest(test);
        q.setText(text);
        return questionRepository.save(q);
    }

    @Transactional
    public void updateQuestion(Question q, String newText) {
        q.setText(newText);
        questionRepository.save(q);
    }
    @Transactional(readOnly = true)
    public List<Question> getQuestionsByTestId(Long testId) {
        return questionRepository.findQuestionsByTestIdOrdered(testId);
    }

    @Transactional(readOnly = true)
    public List<Question> getQuestionsWithAnswersByTest(Test test) {
        return questionRepository.findAllByTest(test);
    }

    @Transactional
    public void deleteQuestion(Long questionId) {
        questionRepository.deleteById(questionId);
    }
}
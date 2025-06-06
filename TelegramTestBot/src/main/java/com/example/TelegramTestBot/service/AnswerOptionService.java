package com.example.TelegramTestBot.service;

import com.example.TelegramTestBot.model.AnswerOption;
import com.example.TelegramTestBot.model.Question;
import com.example.TelegramTestBot.repository.AnswerOptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AnswerOptionService {

    private final AnswerOptionRepository answerOptionRepository;

    @Transactional(readOnly = true)
    public List<AnswerOption> getAnswersForQuestion(Long questionId) {
        return answerOptionRepository.findByQuestionId(questionId);
    }

    @Transactional(readOnly = true)
    public List<AnswerOption> getAnswersForQuestion(Question question) {
        return answerOptionRepository.findByQuestionOrderByOptionNumber(question);
    }

    @Transactional
    public AnswerOption addAnswer(Question question,
                                  String   text,
                                  boolean  correct) {
        int nextNumber = getAnswersForQuestion(question).size() + 1;
        AnswerOption option = new AnswerOption(text, nextNumber, correct);
        option.setQuestion(question);
        if (correct) {
            setCorrectAnswer(question, nextNumber);
        }
        return answerOptionRepository.save(option);
    }
    @Transactional
    public void deleteAnswer(Question question, int optionNumber) {

        answerOptionRepository.deleteByQuestionAndOptionNumber(question, optionNumber);

        // Перенумеровываем оставшиеся варианты «1…N»
        List<AnswerOption> remaining =
                answerOptionRepository.findByQuestionOrderByOptionNumber(question);

        int idx = 1;
        for (AnswerOption ao : remaining) {
            if (ao.getOptionNumber() != idx) {
                ao.setOptionNumber(idx);
                answerOptionRepository.save(ao);
            }
            idx++;
        }
        if (question.getCorrectAnswer() != null
                && question.getCorrectAnswer() == optionNumber) {
            question.setCorrectAnswer(null);
        }
    }
    @Transactional
    public void setCorrectAnswer(Question question, int optionNumber) {

        List<AnswerOption> options =
                answerOptionRepository.findByQuestionOrderByOptionNumber(question);

        for (AnswerOption ao : options) {
            ao.setIsCorrect(ao.getOptionNumber() == optionNumber);
            answerOptionRepository.save(ao);
        }
        question.setCorrectAnswer(optionNumber);
    }
}

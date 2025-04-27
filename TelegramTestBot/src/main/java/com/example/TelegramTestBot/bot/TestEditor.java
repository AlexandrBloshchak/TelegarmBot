package bot;
import java.util.List;
import java.util.Map;

import citations.*;// предполагаю, что здесь есть необходимые классы и интерфейсы

public class TestEditor extends BotApiMethod {
    @BotApiMethod.name("my_tests")
    public SendMessage myTests(Long chatId) throws IOException {
        Map<Long, String> userTestIds = new HashMap<>();

        List<Test> tests = getAvailableTests(chatId);
        for (Test test : tests) {
            if (test.isCreatedBy(user)) {
                userTestIds.put(test.getId(), test.getTitle());
            }
        }

        StringBuilder text = new StringBuilder();
        boolean first = true;
        for (String title : userTestIds.keySet()) {
            String questionId = userTestIds.get(title);
            TestResult result = getResult(chatId, questionId);

            if (!first) {
                text.append("\n");
            } else {
                text.append("Ваши результаты:\n");
            }

            text.append(result.toString());

            first = false;
        }

        return sendMessage(chatId, text);
    }

    @BotApiMethod.name("delete_test")
    public SendMessage deleteTest(Long chatId) throws IOException {
        if (!isCurrentUserAdmin(chatId)) {
            throw new SendMessage(chatId, "Неверный чек-код");
        }

        boolean deleted = deleteTest(chatId);
        if (deleted) {
            log.info("Тест удален: %s", chatId);
            return createSuccessMessage(chatId, "✅ Тест успешно удален!");
        } else {
            log.error("Ошибка при удалении теста: %d", e.getHashCode());
            return createErrorMessage(chatId, "Возможно, произошла ошибка в базе данных");
        }
    }

    @BotApiMethod.name("edit_question")
    public Message editQuestion(Long chatId, Integer questionIndex) throws IOException {
        Question currentQuestion = getSession().getCurrentQuestion();

        if (currentQuestion.getTitle() != null &&
                currentQuestion.getAnswers().size() > 0 &&
                questionIndex >= 1 && questionIndex <= currentQuestion.getTotalQuestions()) {

            String[] options = new String[currentQuestion.getAnswers().stream()
                    .filter(ans -> !ans.isBlank())
                    .map(Question::getText)
                    .toArray(Question[].class));

            String text = "";
            if (!options.length == 0) {
                text += "Ваши ответы:\n";

                for (int i = 0; i < options.length; i++) {
                    text += "[bold]Вопрос " + (i+1) + ":"[/bold]\n" +
                    "[i]" + options[i] + "[/i]\n\n";
                }
            } else {
                text += "[bold]Вопрос " + questionIndex + ":"[/bold]\n"
                        + currentQuestion.getText();
            }

            return sendText(chatId, text);
        }
    }

package view;

import controller.AccountController;
import controller.Background;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;

import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;
import model.SecurityQuestion;
import service.AccountSecurityService.ChangePasswordResult;
import service.AccountSecurityService.EmailChange;
import service.AccountService;
import service.AuthService;
import service.SecurityQuestionService.SetResult;
import view.ui.UI;

import java.util.List;
import java.util.Optional;

/**
 * Settings "Sign-in and security": the email (read-only, changed only through "Change email", which
 * asks the security question), "Change password" and the security question. Each action opens its
 * form in place; only one is open at a time. All account calls run off the JavaFX thread.
 */
public class AccountSecurityView {

    /** Shown under "Change email" while the account has no security question. */
    public static final String EMAIL_NEEDS_QUESTION =
            "Set a security question below first. We ask it before changing your email, to check it's you.";

    private final int userId;
    private final AccountController controller = new AccountController();

    /** The user's question; null while loading or when none is set. */
    private final ObjectProperty<SecurityQuestion> question = new SimpleObjectProperty<>();
    private final BooleanProperty loaded = new SimpleBooleanProperty(false);
    private final BooleanProperty busy = new SimpleBooleanProperty(false);

    private final Label emailValue = UI.body("");
    private final Label status = new Label();
    private final List<VBox> forms = new java.util.ArrayList<>();

    public AccountSecurityView(int userId) {
        this.userId = userId;
    }

    public Node getPage() {
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        status.setAlignment(Pos.CENTER);
        status.setTextAlignment(TextAlignment.CENTER);
        status.managedProperty().bind(status.textProperty().isNotEmpty());
        status.visibleProperty().bind(status.managedProperty());

        emailValue.setWrapText(true);
        emailValue.setMaxWidth(Double.MAX_VALUE);
        emailValue.setAccessibleText("Email");

        VBox page = new VBox(10,
                emailSection(),
                new Separator(),
                passwordSection(),
                new Separator(),
                questionSection(),
                status);
        load();
        return page;
    }

    private void load() {
        Background.run(() -> {
            AccountService.AccountInfo info = controller.loadAccount(userId);
            Optional<SecurityQuestion> q = controller.getQuestion(userId);
            return new Object[]{info == null ? null : info.email, q.orElse(null)};
        }, r -> {
            emailValue.setText(r[0] == null ? "" : (String) r[0]);
            question.set((SecurityQuestion) r[1]);
            loaded.set(true);
        }, ex -> showError("We couldn't reach the server. Please try again."));
    }

    // ------------------------------------------------------------------ email

    private Node emailSection() {
        Label answerQuestion = UI.wrappingLabel("");
        answerQuestion.getStyleClass().add("form-label");
        answerQuestion.textProperty().bind(Bindings.createStringBinding(
                () -> question.get() == null ? "" : question.get().prompt(), question));
        TextField answer = UI.textField("");
        answer.setPromptText("Your answer");
        answer.setAccessibleText("Security answer");
        TextField newEmail = UI.textField("");
        newEmail.setPromptText("New email");
        newEmail.setAccessibleText("New email");
        Label formMsg = formMessage();

        Button submit = UI.primaryButton("Change email");
        submit.disableProperty().bind(busy.or(answer.textProperty().isEmpty()).or(newEmail.textProperty().isEmpty()));
        Button cancel = UI.secondaryButton("Cancel");

        VBox form = form(formLabel("Security question"), answerQuestion, answer,
                formLabel("New email"), newEmail, formMsg, submit, cancel);

        Button open = UI.secondaryButton("Change email");
        open.disableProperty().bind(loaded.not().or(question.isNull()));
        Label needsQuestion = UI.muted(EMAIL_NEEDS_QUESTION);
        needsQuestion.setWrapText(true);
        needsQuestion.visibleProperty().bind(loaded.and(question.isNull()));
        needsQuestion.managedProperty().bind(needsQuestion.visibleProperty());

        open.setOnAction(e -> {
            openOnly(form);
            answer.requestFocus();
        });
        cancel.setOnAction(e -> {
            answer.clear();
            newEmail.clear();
            formMsg.setText("");
            form.setVisible(false);
        });
        answer.textProperty().addListener((o, a, b) -> formMsg.setText(""));
        newEmail.textProperty().addListener((o, a, b) -> formMsg.setText(""));

        submit.setOnAction(e -> {
            String a = answer.getText();
            String em = newEmail.getText();
            busy.set(true);
            Background.run(() -> controller.changeEmail(userId, a, em), (EmailChange r) -> {
                busy.set(false);
                if (r.success()) {
                    emailValue.setText(r.newEmail());
                    answer.clear();
                    newEmail.clear();
                    form.setVisible(false);
                    showSaved("Your email is now " + r.newEmail() + ".");
                } else {
                    // wrong answers say how many tries are left; a lockout says until when
                    formMsg.getStyleClass().setAll(UI.ERROR);
                    formMsg.setText(r.message());
                    if (r.result() == service.AccountSecurityService.ChangeEmailResult.WRONG_ANSWER) {
                        answer.selectAll();
                        answer.requestFocus();
                    }
                }
            }, ex -> {
                busy.set(false);
                formMsg.getStyleClass().setAll(UI.ERROR);
                formMsg.setText("We couldn't reach the server. Please try again.");
            });
        });

        return new VBox(8, formLabel("Email"), emailValue, opener(open, form), needsQuestion, form);
    }

    // ------------------------------------------------------------------ password

    private Node passwordSection() {
        PasswordField current = new PasswordField();
        current.setPromptText("Current password");
        current.setAccessibleText("Current password");
        PasswordField next = new PasswordField();
        next.setPromptText("New password");
        next.setAccessibleText("New password");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Confirm new password");
        confirm.setAccessibleText("Confirm new password");
        for (PasswordField f : List.of(current, next, confirm)) f.getStyleClass().add(UI.TEXT_FIELD);

        // Same rules line as sign-up: muted while fine, red once the typed password breaks them.
        Label rules = UI.muted(AuthService.PASSWORD_RULES);
        rules.setWrapText(true);
        next.textProperty().addListener((o, a, p) -> {
            boolean weak = p != null && !p.isEmpty() && !AuthService.isStrongEnoughPassword(p);
            rules.getStyleClass().setAll("label", weak ? UI.ERROR : UI.MUTED);
        });
        Label mismatch = UI.error("");
        mismatch.setWrapText(true);
        BooleanBinding differs = Bindings.createBooleanBinding(
                () -> !confirm.getText().isEmpty() && !confirm.getText().equals(next.getText()),
                next.textProperty(), confirm.textProperty());
        mismatch.textProperty().bind(Bindings.when(differs).then("Passwords don't match.").otherwise(""));
        mismatch.managedProperty().bind(differs);
        mismatch.visibleProperty().bind(differs);
        Label formMsg = formMessage();

        Button submit = UI.primaryButton("Change password");
        submit.disableProperty().bind(busy.or(current.textProperty().isEmpty()).or(next.textProperty().isEmpty())
                .or(confirm.textProperty().isEmpty()).or(differs));
        Button cancel = UI.secondaryButton("Cancel");

        VBox form = form(formLabel("Current password"), current, formLabel("New password"), next, rules,
                formLabel("Confirm new password"), confirm, mismatch, formMsg, submit, cancel);

        Button open = UI.secondaryButton("Change password");
        open.setOnAction(e -> {
            openOnly(form);
            current.requestFocus();
        });
        Runnable clear = () -> {
            current.clear();
            next.clear();
            confirm.clear();
            formMsg.setText("");
        };
        cancel.setOnAction(e -> {
            clear.run();
            form.setVisible(false);
        });
        for (PasswordField f : List.of(current, next, confirm)) f.textProperty().addListener((o, a, b) -> formMsg.setText(""));

        submit.setOnAction(e -> {
            String cur = current.getText();
            String nw = next.getText();
            busy.set(true);
            Background.run(() -> controller.changePassword(userId, cur, nw), r -> {
                busy.set(false);
                if (r == ChangePasswordResult.SUCCESS) {
                    clear.run();
                    form.setVisible(false);
                    showSaved("Your password has been changed.");
                } else {
                    // clear first: editing a field clears the message
                    if (r == ChangePasswordResult.WRONG_CURRENT_PASSWORD) {
                        current.clear();
                        current.requestFocus();
                    }
                    formMsg.getStyleClass().setAll(UI.ERROR);
                    formMsg.setText(r.message());
                }
            }, ex -> {
                busy.set(false);
                formMsg.getStyleClass().setAll(UI.ERROR);
                formMsg.setText(ChangePasswordResult.DB_ERROR.message());
            });
        });

        return new VBox(8, formLabel("Password"), opener(open, form), form);
    }

    // ------------------------------------------------------------------ security question

    private Node questionSection() {
        Label currentQuestion = UI.wrappingLabel("");
        currentQuestion.textProperty().bind(Bindings.createStringBinding(() -> {
            if (!loaded.get()) return "Loading…";
            return question.get() == null ? "Not set yet." : question.get().prompt();
        }, question, loaded));

        ComboBox<SecurityQuestion> choice = SecurityQuestionChoice.create();
        TextField answer = UI.textField("");
        answer.setPromptText("Your answer");
        answer.setAccessibleText("New security answer");
        // Only asked when replacing an existing question.
        PasswordField password = new PasswordField();
        password.getStyleClass().add(UI.TEXT_FIELD);
        password.setPromptText("Current password");
        password.setAccessibleText("Current password for security question");
        Label passwordLabel = formLabel("Current password");
        for (Node n : List.of(password, passwordLabel)) {
            n.visibleProperty().bind(question.isNotNull());
            n.managedProperty().bind(question.isNotNull());
        }
        Label why = UI.muted(SecurityQuestionChoice.WHY);
        why.setWrapText(true);
        Label formMsg = formMessage();

        Button submit = UI.primaryButton("Save security question");
        submit.disableProperty().bind(busy.or(choice.valueProperty().isNull()).or(answer.textProperty().isEmpty())
                .or(question.isNotNull().and(password.textProperty().isEmpty())));
        Button cancel = UI.secondaryButton("Cancel");

        VBox form = form(formLabel("Question"), choice, why, formLabel("Answer"), answer,
                passwordLabel, password, formMsg, submit, cancel);

        Button open = UI.secondaryButton("");
        open.textProperty().bind(Bindings.when(question.isNull())
                .then("Set security question").otherwise("Change security question"));
        open.disableProperty().bind(loaded.not());
        Runnable clear = () -> {
            answer.clear();
            password.clear();
            formMsg.setText("");
        };
        open.setOnAction(e -> {
            clear.run();
            choice.setValue(question.get());
            openOnly(form);
            choice.requestFocus();
        });
        cancel.setOnAction(e -> {
            clear.run();
            form.setVisible(false);
        });
        answer.textProperty().addListener((o, a, b) -> formMsg.setText(""));
        password.textProperty().addListener((o, a, b) -> formMsg.setText(""));
        choice.valueProperty().addListener((o, a, b) -> formMsg.setText(""));

        submit.setOnAction(e -> {
            String problem = SecurityQuestionChoice.answerProblem(answer.getText());
            if (!problem.isEmpty()) {
                formMsg.getStyleClass().setAll(UI.ERROR);
                formMsg.setText(problem);
                return;
            }
            SecurityQuestion q = choice.getValue();
            String a = answer.getText();
            boolean replacing = question.get() != null;
            String pw = password.getText();
            busy.set(true);
            Background.run(() -> replacing
                    ? controller.changeSecurityQuestion(userId, pw, q.key(), a)
                    : controller.setSecurityQuestion(userId, q.key(), a), r -> {
                busy.set(false);
                if (r == SetResult.SUCCESS) {
                    clear.run();
                    form.setVisible(false);
                    question.set(q);
                    showSaved("Your security question has been updated.");
                } else {
                    // clear first: editing a field clears the message
                    if (r == SetResult.WRONG_PASSWORD) {
                        password.clear();
                        password.requestFocus();
                    }
                    formMsg.getStyleClass().setAll(UI.ERROR);
                    formMsg.setText(r.message());
                }
            }, ex -> {
                busy.set(false);
                formMsg.getStyleClass().setAll(UI.ERROR);
                formMsg.setText(SetResult.DB_ERROR.message());
            });
        });

        return new VBox(8, formLabel("Security question"), currentQuestion, opener(open, form), form);
    }

    // ------------------------------------------------------------------ helpers

    /** A hidden form that takes no space until opened. */
    private VBox form(Node... children) {
        VBox form = new VBox(8);
        for (Node n : children) {
            if (n instanceof Button b) {
                // buttons keep the page's centred, fixed-width look
                form.getChildren().add(centred(b));
            } else {
                if (n instanceof TextInputControl || n instanceof ComboBoxBase<?>) ((Control) n).setMaxWidth(Double.MAX_VALUE);
                form.getChildren().add(n);
            }
        }
        form.getStyleClass().add(UI.CARD_INNER);
        form.setVisible(false);
        form.managedProperty().bind(form.visibleProperty());
        forms.add(form);
        return form;
    }

    private static VBox centred(Button b) {
        VBox box = new VBox(b);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /** The button that opens a form; hidden while that form is open (the form has its own buttons). */
    private static VBox opener(Button open, VBox form) {
        VBox box = centred(open);
        box.visibleProperty().bind(form.visibleProperty().not());
        box.managedProperty().bind(box.visibleProperty());
        return box;
    }

    private void openOnly(VBox form) {
        status.setText("");
        for (VBox f : forms) f.setVisible(f == form);
    }

    private static Label formMessage() {
        Label l = new Label();
        l.setWrapText(true);
        l.setMaxWidth(Double.MAX_VALUE);
        l.managedProperty().bind(l.textProperty().isNotEmpty());
        l.visibleProperty().bind(l.managedProperty());
        return l;
    }

    private static Label formLabel(String text) {
        return AccountView.formLabel(text);
    }

    private void showSaved(String detail) {
        status.getStyleClass().setAll(UI.INLINE_SAVE_CONFIRM);
        UI.showSaveConfirmation(status, detail);
    }

    private void showError(String text) {
        status.getStyleClass().setAll(UI.ERROR);
        status.setText(text);
    }
}

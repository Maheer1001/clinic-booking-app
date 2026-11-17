package service;

import dao.UserRepository;
import dao.UserDAO;

import java.sql.SQLException;

public class UserService {

    private final UserRepository userDAO;

    public UserService() {
        this(new UserDAO());
    }

    public UserService(UserRepository userDAO) {
        this.userDAO = userDAO;
    }

    public void deleteUser(int userId) throws SQLException {
        userDAO.deleteUserById(userId);
    }
}
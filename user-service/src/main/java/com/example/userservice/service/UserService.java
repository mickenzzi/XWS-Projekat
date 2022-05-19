package com.example.userservice.service;

import com.example.userservice.dto.UserDTO;
import com.example.userservice.model.User;
import com.example.userservice.model.VerificationToken;
import com.example.userservice.repository.TokenRepository;
import com.example.userservice.repository.UserRepository;
import com.example.userservice.service.validation.EmailValidator;
import com.example.userservice.service.validation.UsernameValidator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private TokenRepository tokenRepository;

    private final EmailValidator emailValidator = new EmailValidator();
    @Autowired
    private final EmailService emailService = new EmailService();
    private final UsernameValidator usernameValidator = new UsernameValidator();

    public PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }

    public UserDTO getUser(String id) {
        User user = userRepository.findById(id).orElse(null);
        if (user != null)
            return new UserDTO(user);
        else
            return null;
    }

    public User getUserByUsername(String username) {
        User user = userRepository.findByUsername(username);
        if (user != null)
            return user;
        else
            return null;
    }


    public List<UserDTO> getAllUsers() {

        List<User> users = userRepository.findAll();
        List<UserDTO> usersDTO = new ArrayList<>();

        for (User user : users) {
            usersDTO.add(new UserDTO(user));
        }

        return usersDTO;
    }

    public List<UserDTO> getAllPublicProfiles(){
        List<User> users = userRepository.findAll();
        List<UserDTO> usersDTO = new ArrayList<>();

        for(User user : users){
            if(user.isPublic)
                usersDTO.add(new UserDTO(user));
        }

        return usersDTO;
    }

    public UserDTO addUser(UserDTO newUserDTO) {

        //Validation
        validate(newUserDTO);

        //Password hashing (BCrypt)
        newUserDTO.password = passwordEncoder().encode(newUserDTO.password);

        User newUser = new User(newUserDTO);
        userRepository.save(newUser);

        //EmailConfirmation
        VerificationToken verificationToken = new VerificationToken(
                LocalDateTime.now(),
                LocalDateTime.now().plusMinutes(15),
                newUser);

        tokenRepository.save(verificationToken);

        //TODO: Send E-mail
        String confirmationLink = "http://localhost:8080/token?tokenID=" + verificationToken.token;
        emailService.send(newUserDTO.email, confirmationLink);

        return new UserDTO(newUser);

    }

    public void enableUser(String id){
        User user = userRepository.findById(id).orElseThrow(() -> new IllegalStateException("Token not found!"));
        user.isVerified = true;
        userRepository.save(user);
    }

    private void validate(UserDTO newUserDTO){
        String error = "";

        if(!emailValidator.test(newUserDTO.email))
            error += "E-mail not valid!\n";

        if(!usernameValidator.test(newUserDTO.username))
            error += "Username not valid!\n";

        if (usernameExists(newUserDTO.username) || emailExists((newUserDTO.email)))
            error += "Username or Email already exists!\n";

        //TODO: Password Validation

        if(!error.equals(""))
            throw new IllegalStateException("\n" + error);
    }

    private boolean usernameExists(String username) {
        return userRepository.findByUsername(username) != null;
    }

    private boolean emailExists(String email) {
        return userRepository.findByEmail(email) != null;
    }


    public boolean updateUser(UserDTO updateUserDTO) {
        boolean status = userRepository.existsById(updateUserDTO.id);

        if (status) {
            User userToUpdate = userRepository.findById(updateUserDTO.id).orElse(null);
            assert userToUpdate != null;
            userToUpdate.firstName = updateUserDTO.firstName;
            userToUpdate.lastName = updateUserDTO.lastName;
            userToUpdate.username = updateUserDTO.username;
            userToUpdate.password = passwordEncoder().encode(updateUserDTO.password);
            userToUpdate.address = updateUserDTO.address;
            userToUpdate.email = updateUserDTO.email;
            userToUpdate.dateOfBirth = updateUserDTO.dateOfBirth;
            userToUpdate.phoneNumber = updateUserDTO.phoneNumber;
            userToUpdate.gender = updateUserDTO.gender;
            userToUpdate.isPublic = updateUserDTO.isPublic;

            userToUpdate.profile = updateUserDTO.profile;

            userRepository.save(userToUpdate);
        }

        return status;

    }

    public boolean deleteUser(String id) {
        boolean status = userRepository.existsById(id);
        if (status)
            userRepository.deleteById(id);

        return status;

    }

    public String followUser(String subjectId, String targetId){
        String response = "";
        User target = userRepository.findById(targetId).orElse(null);
        User subject = userRepository.findById(subjectId).orElse(null);

        if(target == null) response = "User you want to follow cannot be found!";
        else if (subject == null)  response = "User can not be found!";
        else {

            if(!target.isPublic){
                target.followRequests.add(subjectId);
                userRepository.save(target);
                response = "Request to follow " + target.username + " is pending...";
            } else {

                addFollower(subject, target);
                response = "You successfully followed " + target.username;
            }
        }
        return response;
    }

    //Subject: The user that sent the request
    //Target: The user subject wants to follow
    public String manageRequest(String subjectId, String targetId, boolean response){
        String responseMessage = "";
        User target = userRepository.findById(targetId).orElse(null);
        User subject = userRepository.findById(subjectId).orElse(null);
        assert target != null;
        assert subject != null;

        if(response){

            target.followRequests.remove(subjectId);
            addFollower(subject, target);
            responseMessage = subject.username + " is now following you";
            //TODO: Informisati korisnika da mu je zahtev odobren - Poruke/notifikacije
        }
        else{

            target.followRequests.remove(subjectId);
            userRepository.save(target);
            responseMessage = "You declined request of " + subject.username + " :(";
        }

        return responseMessage;
    }

    private void addFollower(User subject, User target){

        target.followers.add(subject.id);
        subject.following.add(target.id);
        userRepository.save(target);
        userRepository.save(subject);
    }

    private String buildEmail(String name, String link) {
        return "<div style=\"font-family:Helvetica,Arial,sans-serif;font-size:16px;margin:0;color:#0b0c0c\">\n" +
                "\n" +
                "<span style=\"display:none;font-size:1px;color:#fff;max-height:0\"></span>\n" +
                "\n" +
                "  <table role=\"presentation\" width=\"100%\" style=\"border-collapse:collapse;min-width:100%;width:100%!important\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\">\n" +
                "    <tbody><tr>\n" +
                "      <td width=\"100%\" height=\"53\" bgcolor=\"#0b0c0c\">\n" +
                "        \n" +
                "        <table role=\"presentation\" width=\"100%\" style=\"border-collapse:collapse;max-width:580px\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" align=\"center\">\n" +
                "          <tbody><tr>\n" +
                "            <td width=\"70\" bgcolor=\"#0b0c0c\" valign=\"middle\">\n" +
                "                <table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse\">\n" +
                "                  <tbody><tr>\n" +
                "                    <td style=\"padding-left:10px\">\n" +
                "                  \n" +
                "                    </td>\n" +
                "                    <td style=\"font-size:28px;line-height:1.315789474;Margin-top:4px;padding-left:10px\">\n" +
                "                      <span style=\"font-family:Helvetica,Arial,sans-serif;font-weight:700;color:#ffffff;text-decoration:none;vertical-align:top;display:inline-block\">Confirm your email</span>\n" +
                "                    </td>\n" +
                "                  </tr>\n" +
                "                </tbody></table>\n" +
                "              </a>\n" +
                "            </td>\n" +
                "          </tr>\n" +
                "        </tbody></table>\n" +
                "        \n" +
                "      </td>\n" +
                "    </tr>\n" +
                "  </tbody></table>\n" +
                "  <table role=\"presentation\" class=\"m_-6186904992287805515content\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse;max-width:580px;width:100%!important\" width=\"100%\">\n" +
                "    <tbody><tr>\n" +
                "      <td width=\"10\" height=\"10\" valign=\"middle\"></td>\n" +
                "      <td>\n" +
                "        \n" +
                "                <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse\">\n" +
                "                  <tbody><tr>\n" +
                "                    <td bgcolor=\"#1D70B8\" width=\"100%\" height=\"10\"></td>\n" +
                "                  </tr>\n" +
                "                </tbody></table>\n" +
                "        \n" +
                "      </td>\n" +
                "      <td width=\"10\" valign=\"middle\" height=\"10\"></td>\n" +
                "    </tr>\n" +
                "  </tbody></table>\n" +
                "\n" +
                "\n" +
                "\n" +
                "  <table role=\"presentation\" class=\"m_-6186904992287805515content\" align=\"center\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"border-collapse:collapse;max-width:580px;width:100%!important\" width=\"100%\">\n" +
                "    <tbody><tr>\n" +
                "      <td height=\"30\"><br></td>\n" +
                "    </tr>\n" +
                "    <tr>\n" +
                "      <td width=\"10\" valign=\"middle\"><br></td>\n" +
                "      <td style=\"font-family:Helvetica,Arial,sans-serif;font-size:19px;line-height:1.315789474;max-width:560px\">\n" +
                "        \n" +
                "            <p style=\"Margin:0 0 20px 0;font-size:19px;line-height:25px;color:#0b0c0c\">Hi " + name + ",</p><p style=\"Margin:0 0 20px 0;font-size:19px;line-height:25px;color:#0b0c0c\"> Thank you for registering. Please click on the below link to activate your account: </p><blockquote style=\"Margin:0 0 20px 0;border-left:10px solid #b1b4b6;padding:15px 0 0.1px 15px;font-size:19px;line-height:25px\"><p style=\"Margin:0 0 20px 0;font-size:19px;line-height:25px;color:#0b0c0c\"> <a href=\"" + link + "\">Activate Now</a> </p></blockquote>\n Link will expire in 15 minutes. <p>See you soon</p>" +
                "        \n" +
                "      </td>\n" +
                "      <td width=\"10\" valign=\"middle\"><br></td>\n" +
                "    </tr>\n" +
                "    <tr>\n" +
                "      <td height=\"30\"><br></td>\n" +
                "    </tr>\n" +
                "  </tbody></table><div class=\"yj6qo\"></div><div class=\"adL\">\n" +
                "\n" +
                "</div></div>";
    }

}

package com.identity_service.identity.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.identity_service.identity.dto.request.UserCreationRequest;
import com.identity_service.identity.dto.response.UserResponse;
import com.identity_service.identity.model.entity.User;
import com.identity_service.identity.model.enums.UserStatus;
import com.identity_service.identity.service.IUserService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.time.LocalDate;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource("/test.properties")
@Slf4j
public class UserControllerTest {
    @Autowired
    private  MockMvc mockMvc;

    @MockitoBean
    private IUserService userService;
    private UserCreationRequest request;
    private UserResponse response;
    private User user;

    @BeforeEach
    void initData(){
        request = UserCreationRequest.builder()
                .userName("kingmoan")
                .avatar("")
                .password("123456")
                .email("sigmafreaky@gmail.com")
                .firstName("Johnny")
                .lastName("Sins")
                .gender("Male")
                .dob(LocalDate.of(1990 , 1 , 1))
                .address("135 Nguyen Van Thinh")
                .phone("0982345654")
                .build();

        response = UserResponse.builder()
                //.userId()
                .userName("kingmoan")
                .email("sigmafreaky@gmail.com")
                .emailVerified(false)
                .build();

        user     = User.builder()
                .userId("userid1_uuid")
                .userName("kingmoan")
                .email("sigmafreaky@gmail.com")
                .password("encoded")
                .emailVerified(false)
                .userStatus(UserStatus.INACTIVE)
                .build();

    }

    @Test
    void createUser_validRequest() throws Exception {
        //GIVEN
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String content = objectMapper.writeValueAsString(request);

        //Khi request den Controller thay vi goi userService , no tra ve response minh hoa thay userService
        Mockito.when(userService.createUser(ArgumentMatchers.any()))
                        .thenReturn(response);

        //WHEN
        mockMvc.perform(MockMvcRequestBuilders
                .post("/user/create")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(content))
                //THEN
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code")
                .value(1000)
        );
        //THEN..
    }

    @Test
    void createUser_userNameInvalid() throws Exception {
        //GIVEN
        request.setUserName("tay");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String content = objectMapper.writeValueAsString(request);

        //WHEN
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/user/create")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(content))
                //THEN
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code")
                        .value(1014 )
                );
        //
    }

    @Test
    void createUser_passwordInvalid() throws Exception {
        //GIVEN
        request.setPassword("123");
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        String content = objectMapper.writeValueAsString(request);

        //WHEN
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/user/create")
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                        .content(content))
                //THEN
                .andExpect(MockMvcResultMatchers.status().isBadRequest())
                .andExpect(MockMvcResultMatchers.jsonPath("code")
                        .value(1015 )
                );
        //
    }

    @Test
    void getUserById_success() throws Exception {
        //GIVEN
        String userId = user.getUserId();
        //Khi request den Controller thay vi goi userService , no tra ve response minh hoa thay userService
        Mockito.when(userService.getUser(ArgumentMatchers.any()))
                .thenReturn(response);

        //WHEN
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/user/getById")
                        .param("userId" , userId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                //THEN
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code")
                        .value(1000)
                );
        //THEN..
    }


    @Test
    void getUserById_null_fail() throws Exception {
        //GIVEN
        String userId = "";
        //WHEN
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/user/getById")
                        .param("userId" , userId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                //THEN
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
        Mockito.verify(userService, Mockito.never()).getUser(Mockito.any());
    }

    @Test
    void deleteUserById_success() throws Exception {
        //GIVEN
        String userId = user.getUserId();
        Mockito.doNothing().when(userService).deleteUser(userId);
        //WHEN
        mockMvc.perform(MockMvcRequestBuilders
                        .delete("/user/delete")
                        .param("userId" , userId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                //THEN
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("code")
                        .value(1000)
                );
        //THEN (Verify interaction)
        Mockito.verify(userService).deleteUser(userId);
    }

    @Test
    void deleteUserById_null_fail() throws Exception {
        //GIVEN
        String userId = "";
        //WHEN
        mockMvc.perform(MockMvcRequestBuilders
                        .delete("/user/delete")
                        .param("userId" , userId)
                        .contentType(MediaType.APPLICATION_JSON_VALUE))
                //THEN
                .andExpect(MockMvcResultMatchers.status().isBadRequest());
        Mockito.verify(userService, Mockito.never()).getUser(Mockito.any());
    }


}

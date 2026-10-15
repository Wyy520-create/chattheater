package com.chattheater;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 群聊剧场 ChatTheater 后端入口。
 */
@EnableScheduling
@SpringBootApplication
@MapperScan("com.chattheater.**.mapper")
public class ChatTheaterApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatTheaterApplication.class, args);
    }
}

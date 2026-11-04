package com.insure.ai;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 保险智能客服系统 启动类
 *
 * @author insurance-ai
 */
@EnableAsync
@EnableTransactionManagement
@MapperScan("com.insure.ai.modules.**.mapper")
@SpringBootApplication
public class InsuranceAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsuranceAiApplication.class, args);
        System.out.println("""
                ----------------------------------------------------
                  保险智能客服系统启动成功
                  接口地址: http://localhost:8080/api
                ----------------------------------------------------
                """);
    }
}

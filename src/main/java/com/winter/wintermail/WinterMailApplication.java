package com.winter.wintermail;

import org.springframework.boot.SpringApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.winter.wintermail.mail")
/** 应用入口：启动 Spring Boot，并扫描邮箱服务、MyBatis Mapper 等组件。 */
public class WinterMailApplication {

    public static void main(String[] args) {
        SpringApplication.run(WinterMailApplication.class, args);
    }

}

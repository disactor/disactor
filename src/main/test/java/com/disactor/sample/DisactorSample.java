package com.disactor.sample;

import org.springframework.context.support.ClassPathXmlApplicationContext;

public class DisactorSample {

    public static void main(String[] args) {
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("classpath:/disactor-sample-spring.xml");
        MyFirstDisactor myFirstDisactor = context.getBean(MyFirstDisactor.class);

    }
}

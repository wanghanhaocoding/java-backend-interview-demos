package com.example.springcoredemo.ioc;

import com.example.springcoredemo.ioc.scan.ScannedGreetingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.stereotype.Service;

import static org.assertj.core.api.Assertions.assertThat;

class BeanDefinitionRegistrationDemoTest {

    @Test
    void componentScanRegistersDefinitionsBeforeInstantiation() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            DefaultListableBeanFactory beanFactory = context.getDefaultListableBeanFactory();
            ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(context);

            scanner.scan("com.example.springcoredemo.ioc.scan");

            assertThat(beanFactory.containsBeanDefinition("scannedGreetingService")).isTrue();
            assertThat(beanFactory.containsSingleton("scannedGreetingService")).isFalse();

            BeanDefinition beanDefinition = beanFactory.getBeanDefinition("scannedGreetingService");
            assertThat(beanDefinition.getBeanClassName()).isEqualTo(ScannedGreetingService.class.getName());
            assertThat(beanDefinition).isInstanceOf(AnnotatedBeanDefinition.class);

            AnnotatedBeanDefinition annotatedBeanDefinition = (AnnotatedBeanDefinition) beanDefinition;
            assertThat(annotatedBeanDefinition.getMetadata().hasAnnotation(Service.class.getName())).isTrue();

            context.refresh();
            ScannedGreetingService service = context.getBean(ScannedGreetingService.class);
            assertThat(service.describeRegistrationFlow())
                    .isEqualTo("component scan -> BeanDefinition -> BeanFactory -> bean instance");
        }
    }
}

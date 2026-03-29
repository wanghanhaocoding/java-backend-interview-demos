package com.example.transactiondemo.ioc;

import com.example.transactiondemo.ioc.scan.ScannedGreetingService;
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
    void componentScanRegistersBeanDefinitionsBeforeBeanInstantiation() {
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            DefaultListableBeanFactory beanFactory = context.getDefaultListableBeanFactory();
            ClassPathBeanDefinitionScanner scanner = new ClassPathBeanDefinitionScanner(context);

            scanner.scan("com.example.transactiondemo.ioc.scan");

            assertThat(beanFactory.containsBeanDefinition("scannedGreetingService")).isTrue();
            assertThat(beanFactory.containsBeanDefinition("scannedGreetingRepository")).isTrue();
            assertThat(beanFactory.containsSingleton("scannedGreetingService")).isFalse();

            BeanDefinition serviceDefinition = beanFactory.getBeanDefinition("scannedGreetingService");
            assertThat(serviceDefinition.getBeanClassName()).isEqualTo(ScannedGreetingService.class.getName());
            assertThat(serviceDefinition.isSingleton()).isTrue();
            assertThat(serviceDefinition.getRole()).isEqualTo(BeanDefinition.ROLE_APPLICATION);
            assertThat(serviceDefinition).isInstanceOf(AnnotatedBeanDefinition.class);

            AnnotatedBeanDefinition annotatedDefinition = (AnnotatedBeanDefinition) serviceDefinition;
            assertThat(annotatedDefinition.getMetadata().hasAnnotation(Service.class.getName())).isTrue();

            context.refresh();

            ScannedGreetingService service = context.getBean(ScannedGreetingService.class);
            assertThat(service.describeRegistrationFlow())
                    .isEqualTo("component scan -> BeanDefinition -> BeanFactory -> bean instance");
            assertThat(beanFactory.containsSingleton("scannedGreetingService")).isTrue();
        }
    }
}

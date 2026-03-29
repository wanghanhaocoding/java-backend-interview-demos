package com.example.springcoredemo.circular;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CircularDependencyDemoTest {

    @Test
    void setterStyleObjectsCanBeConnectedInTwoSteps() {
        CircularDependencyScenarios.SetterA setterA = new CircularDependencyScenarios.SetterA();
        CircularDependencyScenarios.SetterB setterB = new CircularDependencyScenarios.SetterB();

        setterA.setSetterB(setterB);
        setterB.setSetterA(setterA);

        assertThat(setterA.setterB()).isSameAs(setterB);
        assertThat(setterB.setterA()).isSameAs(setterA);
    }

    @Test
    void constructorCycleShouldFailDuringContextRefresh() {
        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBean(CircularDependencyScenarios.ConstructorA.class);
        context.registerBean(CircularDependencyScenarios.ConstructorB.class);

        assertThatThrownBy(context::refresh)
                .isInstanceOf(BeanCreationException.class);
    }
}

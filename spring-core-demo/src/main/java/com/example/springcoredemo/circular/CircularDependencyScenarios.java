package com.example.springcoredemo.circular;

public final class CircularDependencyScenarios {

    private CircularDependencyScenarios() {
    }

    public static final class SetterA {

        private SetterB setterB;

        public void setSetterB(SetterB setterB) {
            this.setterB = setterB;
        }

        public SetterB setterB() {
            return setterB;
        }
    }

    public static final class SetterB {

        private SetterA setterA;

        public void setSetterA(SetterA setterA) {
            this.setterA = setterA;
        }

        public SetterA setterA() {
            return setterA;
        }
    }

    public static final class ConstructorA {

        private final ConstructorB constructorB;

        public ConstructorA(ConstructorB constructorB) {
            this.constructorB = constructorB;
        }

        public ConstructorB constructorB() {
            return constructorB;
        }
    }

    public static final class ConstructorB {

        private final ConstructorA constructorA;

        public ConstructorB(ConstructorA constructorA) {
            this.constructorA = constructorA;
        }

        public ConstructorA constructorA() {
            return constructorA;
        }
    }
}

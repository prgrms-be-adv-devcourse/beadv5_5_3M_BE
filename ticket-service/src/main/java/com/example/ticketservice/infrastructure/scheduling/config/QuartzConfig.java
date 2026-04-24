package com.example.ticketservice.infrastructure.scheduling.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

@Configuration
public class QuartzConfig {

    // Spring Boot auto-config가 만든 SchedulerFactoryBean에 JobFactory만 교체.
    // SchedulerFactoryBeanCustomizer는 Spring Boot 버전별 패키지 차이가 있어 우회.
    @Bean
    public BeanPostProcessor quartzJobFactoryPostProcessor(ApplicationContext applicationContext) {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
                if (bean instanceof SchedulerFactoryBean schedulerFactoryBean) {
                    AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
                    jobFactory.setApplicationContext(applicationContext);
                    schedulerFactoryBean.setJobFactory(jobFactory);
                }
                return bean;
            }
        };
    }

    private static class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory implements ApplicationContextAware {

        private transient ApplicationContext applicationContext;

        @Override
        public void setApplicationContext(ApplicationContext context) throws BeansException {
            this.applicationContext = context;
        }

        @Override
        protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
            Class<?> jobClass = bundle.getJobDetail().getJobClass();
            try {
                return applicationContext.getBean(jobClass);
            } catch (NoSuchBeanDefinitionException e) {
                Object job = super.createJobInstance(bundle);
                applicationContext.getAutowireCapableBeanFactory().autowireBean(job);
                return job;
            }
        }
    }
}

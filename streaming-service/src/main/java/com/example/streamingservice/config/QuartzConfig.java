package com.example.streamingservice.config;

import org.jspecify.annotations.NonNull;
import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.quartz.autoconfigure.SchedulerFactoryBeanCustomizer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

@Configuration
public class QuartzConfig {

	@Bean
	public SchedulerFactoryBeanCustomizer springBeanJobFactoryCustomizer(ApplicationContext applicationContext) {
		AutowiringSpringBeanJobFactory jobFactory = new AutowiringSpringBeanJobFactory();
		jobFactory.setApplicationContext(applicationContext);
		return schedulerFactoryBean -> schedulerFactoryBean.setJobFactory(jobFactory);
	}

	private static class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory implements ApplicationContextAware {

		private transient ApplicationContext applicationContext;

		@Override
		public void setApplicationContext(@NonNull ApplicationContext context) throws BeansException {
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
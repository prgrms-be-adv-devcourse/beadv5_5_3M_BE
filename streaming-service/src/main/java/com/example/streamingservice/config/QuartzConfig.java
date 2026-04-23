package com.example.streamingservice.config;

import org.quartz.spi.TriggerFiredBundle;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.quartz.SpringBeanJobFactory;

@Configuration
public class QuartzConfig {

	@Bean
	public SpringBeanJobFactory springBeanJobFactory(ApplicationContext applicationContext) {
		AutowiringSpringBeanJobFactory factory = new AutowiringSpringBeanJobFactory();
		factory.setBeanFactory(applicationContext.getAutowireCapableBeanFactory());
		return factory;
	}

	/**
	 * Quartz 는 Job 인스턴스를 newInstance() 로 직접 생성하므로 생성자 주입이 불가능.
	 * SpringBeanJobFactory 를 확장해 autowire 까지 수행해 @Autowired / setter 주입을 활성화한다.
	 */
	private static class AutowiringSpringBeanJobFactory extends SpringBeanJobFactory {
		private AutowireCapableBeanFactory beanFactory;

		public void setBeanFactory(AutowireCapableBeanFactory beanFactory) {
			this.beanFactory = beanFactory;
		}

		@Override
		protected Object createJobInstance(TriggerFiredBundle bundle) throws Exception {
			Object job = super.createJobInstance(bundle);
			beanFactory.autowireBean(job);
			return job;
		}
	}
}
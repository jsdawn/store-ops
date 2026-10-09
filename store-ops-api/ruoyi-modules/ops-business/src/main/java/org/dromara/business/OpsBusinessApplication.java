package org.dromara.business;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

/**
 * 门店经营业务模块
 *
 * @author store-ops
 */
@EnableDubbo
@SpringBootApplication
public class OpsBusinessApplication {
    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(OpsBusinessApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
        System.out.println("(♥◠‿◠)ﾉﾞ  门店经营模块启动成功   ლ(´ڡ`ლ)ﾞ  ");
    }
}

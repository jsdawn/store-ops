package org.dromara.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;

/**
 * AI模块
 *
 * @author opensnail
 */
@SpringBootApplication
public class RuoYiAiApplication {

    public static void main(String[] args) {
        SpringApplication application = new SpringApplication(RuoYiAiApplication.class);
        application.setApplicationStartup(new BufferingApplicationStartup(2048));
        application.run(args);
        System.out.println("(♥◠‿◠)ﾉﾞ  AI模块启动成功   ლ(´ڡ`ლ)ﾞ  ");
    }

}

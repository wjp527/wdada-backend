package com.wjp.wdada;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

@SpringBootTest
public class RxJavaTest {

    @Test
    public void test() throws InterruptedException {
        // 创建数据流
        Flowable<Long> flowable = Flowable.interval(1, TimeUnit.SECONDS)
                .map(i -> i+1)
                .subscribeOn(Schedulers.io()); // 指定执行操作使用的线程池

        // 订阅 Flowable 类，并且大衣呢出每个接收到的数字
        flowable
                .subscribeOn(Schedulers.io())
                // 当流每过1s，就会打印出一个数字
                .doOnNext(item -> System.out.println(item.toString()))
                .subscribe();


        // 主线程睡眠，以便观察到结果
        Thread.sleep(3000);

    }
}

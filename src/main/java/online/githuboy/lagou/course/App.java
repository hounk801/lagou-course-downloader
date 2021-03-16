package online.githuboy.lagou.course;

import cn.hutool.core.io.file.FileWriter;
import lombok.extern.slf4j.Slf4j;
import online.githuboy.lagou.course.support.CmdExecutor;
import online.githuboy.lagou.course.support.DownText;
import online.githuboy.lagou.course.support.Downloader;
import online.githuboy.lagou.course.support.ExecutorService;

import java.io.File;
import java.io.IOException;

/**
 * 启动类
 *
 * @author suchu
 * @date 2020/12/11
 */
@Slf4j
public class App {

    //拉钩课程ID
    static String courseId = "510";
    //视频保存的目录
    static String savePath = "D:\\lagou";


    public static void main(String[] args) throws IOException, InterruptedException {
//        downVideo();
        downText();

    }

    private static void downText() throws IOException {
        DownText downloader = new DownText(courseId, savePath);
        Thread logThread = new Thread(() -> {
            while (true) {
                log.info("Thread pool:{}", ExecutorService.getExecutor());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "log-thread");
        logThread.setDaemon(true);
//        logThread.start();   SSL peer shut down incorrectly
        downloader.start();
    }

    private static void downVideo() throws IOException, InterruptedException {
        try {
            log.info("检查ffmpeg是否存在");
            int status = CmdExecutor.executeCmd(new File("."), "ffmpeg", "-version");
        } catch (Exception e) {
            log.error("{}", e.getMessage());
        }
        Downloader downloader = new Downloader(courseId, savePath);
        Thread logThread = new Thread(() -> {
            while (true) {
                log.info("Thread pool:{}", ExecutorService.getExecutor());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }, "log-thread");
        logThread.setDaemon(true);
//        logThread.start();   SSL peer shut down incorrectly
        downloader.start();
    }


}

package online.githuboy.lagou.course.support;

import cn.hutool.core.io.file.FileWriter;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import online.githuboy.lagou.course.domain.LessonInfo;
import online.githuboy.lagou.course.task.VideoInfoLoader;
import online.githuboy.lagou.course.utils.HttpUtils;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 下载器
 *
 * @author suchu
 * @since 2019年8月2日
 */
@Slf4j
public class Downloader {

    private final static String COURSE_INFO_API = "https://gate.lagou.com/v1/neirong/kaiwu/getCourseLessons?courseId={0}";

    private final static String LESSION_INFO_API = "https://gate.lagou.com/v1/neirong/kaiwu/getCourseLessonDetail?lessonId=";

    /**
     * 拉钩课程地址
     */
    @Getter
    private final String courseId;
    /**
     * 保存路径
     */
    @Getter
    private final String savePath;

    private File basePath;

    private final String courseUrl;

    private CountDownLatch latch;

    private final List<LessonInfo> lessonInfoList = new ArrayList<>();

    private final List<LessonInfo> lessonTextList = new ArrayList<>();


    private volatile List<MediaLoader> mediaLoaders;

    private long start;

    /**
     * true video
     * false text
     */
    private boolean videoOrText;

    public Downloader(String courseId, String savePath) {
        this.courseId = courseId;
        this.savePath = savePath;
        this.courseUrl = MessageFormat.format(COURSE_INFO_API, courseId);
    }

    public Downloader(String courseId, String savePath, boolean videoOrText) {
        this.courseId = courseId;
        this.savePath = savePath;
        this.courseUrl = MessageFormat.format(COURSE_INFO_API, courseId);
        this.videoOrText = videoOrText;
    }

    public void start() throws IOException, InterruptedException {
        start = System.currentTimeMillis();
        parseLessonInfo2();
        if (this.videoOrText) {
            parseVideoInfo();
            downloadMedia();
        }
        if (!this.videoOrText) {
            downloadText();
        }
    }

    private void downloadText() {
        lessonTextList.forEach(r -> {
            String strContent = HttpUtils
                    .get(LESSION_INFO_API + r.getLessonId(), CookieStore.getCookie())
                    .header("x-l-req-header", " {deviceType:1}")
                    .execute().body();

            JSONObject jsonObject = JSONObject.parseObject(strContent);
            if (jsonObject.getInteger("state") != 1) {
                throw new RuntimeException("访问课程信息出错:" + strContent);
            }
            jsonObject = jsonObject.getJSONObject("content");
            String textContent = jsonObject.getString("textContent");

            FileWriter writer = FileWriter.create(new File(basePath, r.getLessonName() + ".html"));
            writer.write(textContent);
        });


    }

    private void parseLessonInfo2() throws IOException {
        String strContent = HttpUtils
                .get(courseUrl, CookieStore.getCookie())
                .header("x-l-req-header", " {deviceType:1}")
                .execute().body();
        JSONObject jsonObject = JSONObject.parseObject(strContent);
        if (jsonObject.getInteger("state") != 1) {
            throw new RuntimeException("访问课程信息出错:" + strContent);
        }
        jsonObject = jsonObject.getJSONObject("content");
        String courseName = jsonObject.getString("courseName");
        JSONArray courseSections = jsonObject.getJSONArray("courseSectionList");
        this.basePath = new File(savePath, this.courseId + "_" + courseName);
        if (!basePath.exists()) {
            basePath.mkdirs();
        }
        for (int i = 0; i < courseSections.size(); i++) {
            JSONObject courseSection = courseSections.getJSONObject(i);
            JSONArray courseLessons = courseSection.getJSONArray("courseLessons");
            for (int j = 0; j < courseLessons.size(); j++) {
                JSONObject lesson = courseLessons.getJSONObject(j);
                String lessonName = lesson.getString("theme");
                String status = lesson.getString("status");
                if (!"RELEASE".equals(status)) {
                    log.info("课程:【{}】 [未发布]", lessonName);
                    continue;
                }
                //insert your filter code,use for debug
                String lessonId = lesson.getString("id");

                if (this.videoOrText) {
                    String fileId = "";
                    String fileUrl = "";
                    String fileEdk = "";
                    JSONObject videoMediaDTO = lesson.getJSONObject("videoMediaDTO");
                    if (null != videoMediaDTO) {
                        fileId = videoMediaDTO.getString("fileId");
                        fileUrl = videoMediaDTO.getString("fileUrl");
                        fileEdk = videoMediaDTO.getString("fileEdk");
                    }
                    String appId = lesson.getString("appId");
                    LessonInfo lessonInfo = LessonInfo.builder().lessonId(lessonId).lessonName(lessonName).fileId(fileId).appId(appId).fileEdk(fileEdk).fileUrl(fileUrl).build();
                    lessonInfoList.add(lessonInfo);
                    log.info("解析到课程信息：【{}】,appId:{},fileId:{}", lessonName, appId, fileId);
                }else {
                    LessonInfo lessonText = LessonInfo.builder().lessonId(lessonId).lessonName(lessonName).build();
                    lessonTextList.add(lessonText);
                    downloadText();
                }

            }
        }
        System.out.println(1);
    }

    private void parseVideoInfo() {
        latch = new CountDownLatch(lessonInfoList.size());
        mediaLoaders = new Vector<>();
        lessonInfoList.forEach(lessonInfo -> {
            VideoInfoLoader loader = new VideoInfoLoader(lessonInfo.getLessonName(), lessonInfo.getAppId(), lessonInfo.getFileId(), lessonInfo.getFileUrl(), lessonInfo.getLessonId());
            loader.setM3U8MediaLoaders(mediaLoaders);
            loader.setBasePath(this.basePath);
            loader.setLatch(latch);
            ExecutorService.execute(loader);
        });
    }

    private void downloadMedia() throws InterruptedException {
        log.info("等待获取视频信息任务完成...");
        latch.await();
        if (mediaLoaders.size() != lessonInfoList.size()) {
            log.info("视频META信息没有全部下载成功: success:{},total:{}", mediaLoaders.size(), lessonInfoList.size());
            tryTerminal();
            return;
        }
        log.info("所有视频META信息获取成功 total：{}", mediaLoaders.size());
        CountDownLatch all = new CountDownLatch(mediaLoaders.size());

        for (MediaLoader loader : mediaLoaders) {
            loader.setLatch(all);
            ExecutorService.getExecutor().execute(loader);
        }
        all.await();
        long end = System.currentTimeMillis();
        log.info("所有视频处理耗时:{} s", (end - start) / 1000);
        log.info("视频输出目录:{}", this.basePath.getAbsolutePath());
        System.out.println("\n\n失败统计信息\n\n");
        Stats.failedCount.forEach((key, value) -> System.out.println(key + " -> " + value.get()));
        tryTerminal();
    }

    private void tryTerminal() throws InterruptedException {
        log.info("程序将在{}s后退出", 5);
        ExecutorService.getExecutor().shutdown();
        ExecutorService.getHlsExecutor().shutdown();
        ExecutorService.getHlsExecutor().awaitTermination(5, TimeUnit.SECONDS);
        ExecutorService.getExecutor().awaitTermination(5, TimeUnit.SECONDS);
    }

}

package online.githuboy.lagou.course.support;

import cn.hutool.core.io.file.FileWriter;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import online.githuboy.lagou.course.domain.LessonInfo;
import online.githuboy.lagou.course.utils.HttpUtils;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 下载器
 *
 * @author suchu
 * @since 2019年8月2日
 */
@Slf4j
public class DownText {

    private final static String COURSE_INFO_API = "https://gate.lagou.com/v1/neirong/kaiwu/getCourseLessons?courseId={0}";
    private final static String LESSION_INFO_API = "https://gate.lagou.com/v1/neirong/kaiwu/getCourseLessonDetail?lessonId=";
    final static String regExp="[\n`~!@#$%^&*()+=|{}':;',\\[\\].<>/?~！@#￥%……&*（）——+|{}【】‘；：”“’。， 、？]";

    /**
     * 拉钩视频课程地址
     */
    @Getter
    private final String courseId;
    /**
     * 视频保存路径
     */
    @Getter
    private final String savePath;

    private File basePath;

    private final String courseUrl;

    private CountDownLatch latch;
    private final List<LessonInfo> lessonTextList = new ArrayList<>();

    private long start;

    public DownText(String courseId, String savePath) {
        this.courseId = courseId;
        this.savePath = savePath;
        this.courseUrl = MessageFormat.format(COURSE_INFO_API, courseId);
    }

    public void start() throws IOException {
        start = System.currentTimeMillis();
        parseLessonInfo2();
        downloadText();
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
                String lessonName = lesson.getString("theme").replaceAll(regExp,"");
                String status = lesson.getString("status");
                if (!"RELEASE".equals(status)) {
                    log.info("课程:【{}】 [未发布]", lessonName);
                    continue;
                }
                //insert your filter code,use for debug
                String lessonId = lesson.getString("id");

                LessonInfo lessonText = LessonInfo.builder().lessonId(lessonId).lessonName(lessonName).build();
                lessonTextList.add(lessonText);
            }
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

}

package online.githuboy.lagou.course.domain;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LessonInfo {
    private String lessonId;
    private String lessonName;
    private String courseName;
    private String appId;
    private String fileId;
    private String fileUrl;
    private String fileEdk;
}

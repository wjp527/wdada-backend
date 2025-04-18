package com.wjp.wdada.mapper;

import com.wjp.wdada.model.dto.statistic.AppAnswerCountDTO;
import com.wjp.wdada.model.dto.statistic.AppAnswerResultCountDTO;
import com.wjp.wdada.model.entity.UserAnswer;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
* @author wjp
* @description 针对表【user_answer(用户答题记录)】的数据库操作Mapper
* @createDate 2025-04-07 11:54:19
* @Entity com.wjp.wdada.model.entity.UserAnswer
*/
public interface UserAnswerMapper extends BaseMapper<UserAnswer> {

    /**
     * APP 用户提交答案数统计
     * @return
     */
    @Select("select appId, count(userId) as answerCount from user_answer\n" +
            "group by appId order by answerCount desc limit 10;")
    public List<AppAnswerCountDTO> doAppAnswerCount();

    @Select("select resultName,count(resultName) as resultCount from user_answer\n" +
            "where appId = #{appId}\n" +
            "group by resultName order by resultCount desc limit 10;")
    public List<AppAnswerResultCountDTO> doAppAnswerResultCount(Long appId);

}





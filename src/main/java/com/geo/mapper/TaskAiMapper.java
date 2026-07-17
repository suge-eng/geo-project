package com.geo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.geo.entity.TaskAi;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TaskAiMapper extends BaseMapper<TaskAi> {

    List<TaskAi> selectByTaskNo(@Param("taskNo") String taskNo);
}
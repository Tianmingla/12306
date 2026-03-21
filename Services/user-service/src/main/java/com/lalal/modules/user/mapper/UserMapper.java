package com.lalal.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lalal.modules.user.dao.UserDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}

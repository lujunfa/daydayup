package org.patric.daydayup.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;



@TableName("t_storage")
@Data
public class Storage {
    @TableId(type = IdType.AUTO)
    private Long id;
}

package org.patric.daydayup.serviceImpl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.patric.daydayup.dao.StorageMapper;
import org.patric.daydayup.entity.Storage;
import org.patric.daydayup.storage.StorageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


@Service
public class StorageServiceImpl implements StorageService {

    @Autowired
    private StorageMapper storageMapper;
    @Override
    public void deduct(String commodityCode, int count) {
        Storage storage =  storageMapper.selectOne(Wrappers.<Storage>lambdaQuery().eq(Storage::getId, 1L));
    }
}

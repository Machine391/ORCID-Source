/**
 * =============================================================================
 *
 * ORCID (R) Open Source
 * http://orcid.org
 *
 * Copyright (c) 2012-2013 ORCID, Inc.
 * Licensed under an MIT-Style License (MIT)
 * http://orcid.org/open-source-license
 *
 * This copyright and license information (including a link to the full license)
 * shall be included in its entirety in all copies or substantial portion of
 * the software.
 *
 * =============================================================================
 */
package org.orcid.core.manager.impl;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Resource;

import org.orcid.core.manager.StatisticsGeneratorManager;
import org.orcid.core.manager.StatisticsManager;
import org.orcid.core.utils.statistics.StatisticsEnum;
import org.orcid.persistence.dao.StatisticsGeneratorDao;
import org.orcid.persistence.jpa.entities.StatisticKeyEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatisticsGeneratorManagerImpl implements StatisticsGeneratorManager {

    private static final Logger LOG = LoggerFactory.getLogger(OrcidProfileManagerImpl.class);
    
    @Resource
    private StatisticsGeneratorDao statisticsGeneratorDao;

    @Resource
    private StatisticsManager statisticsManager;
    
    @Override
    public void generateStatistics() {
        LOG.debug("About to run statistics generator thread");
        Map<String, Long> statistics = new HashMap<String, Long>();        
        statistics.put(StatisticsEnum.KEY_LIVE_IDS.value(), statisticsGeneratorDao.getLiveIds());
        statistics.put(StatisticsEnum.KEY_IDS_WITH_VERIFIED_EMAIL.value(), statisticsGeneratorDao.getAccountsWithVerifiedEmails());
        statistics.put(StatisticsEnum.KEY_IDS_WITH_WORKS.value(), statisticsGeneratorDao.getAccountsWithWorks());
        statistics.put(StatisticsEnum.KEY_NUMBER_OF_WORKS.value(), statisticsGeneratorDao.getNumberOfWorks());
        statistics.put(StatisticsEnum.KEY_WORKS_WITH_DOIS.value(), statisticsGeneratorDao.getNumberOfWorksWithDOIs());
        
        StatisticKeyEntity statisticKey = statisticsManager.createHistory();
        
        //Store statistics on database
        for(String key : statistics.keySet()){
            statisticsManager.saveStatistic(statisticKey, key, statistics.get(key));
        } 
    }

}

package org.nonfood.oilplants.service;

import org.junit.jupiter.api.Test;
import org.nonfood.oilplants.dao.TraitDao;
import org.nonfood.oilplants.model.TraitCriterion;
import org.nonfood.oilplants.model.TraitDefinition;
import org.nonfood.oilplants.model.TraitOption;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class TraitServiceValidationTest {
    private final TraitService service=new TraitService(mock(TraitDao.class),new DescriptionParser());

    @Test void rejectsUnknownAndAmbiguousTraitParameters() {
        List<TraitDefinition> catalog=List.of(trait("leaf_shape",option("needle",0)));
        assertThrows(IllegalArgumentException.class,()->service.selections(Map.of("leaf_shape_typo",new String[]{"needle"}),catalog));
        assertThrows(IllegalArgumentException.class,()->service.selections(Map.of("leaf_shape",new String[]{"needle","fan_shaped"}),catalog));
        assertThrows(IllegalArgumentException.class,()->service.selections(Map.of("leaf_shape",new String[]{"needle"},"not_leaf_shape",new String[]{"yes"}),catalog));
    }

    @Test void coverageRequiresEveryRequestedTraitButNotTheExactRequestedOption() {
        List<TraitDefinition> catalog=List.of(
                trait("leaf_shape",option("needle",0),option("fan_shaped",2)),
                trait("flower_color",option("white",0),option("yellow",0)));
        assertTrue(TraitService.hasConfirmedCoverage(List.of(new TraitCriterion("leaf_shape","needle",false)),catalog),
                "another confirmed leaf shape means a needle query is a real no-match, not missing evidence");
        assertTrue(TraitService.hasConfirmedCoverage(List.of(new TraitCriterion("leaf_shape","needle",true)),catalog),
                "a negated option still needs confirmed data for that trait");
        assertFalse(TraitService.hasConfirmedCoverage(List.of(new TraitCriterion("leaf_shape","needle",false),new TraitCriterion("flower_color","white",false)),catalog),
                "one unsupported requested trait makes an empty combined result evidence-limited");
    }

    private static TraitDefinition trait(String code,TraitOption... options) {
        TraitDefinition trait=new TraitDefinition();trait.setCode(code);trait.setOptions(List.of(options));return trait;
    }
    private static TraitOption option(String code,long plants) {
        TraitOption option=new TraitOption();option.setCode(code);option.setConfirmedPlantCount(plants);return option;
    }
}

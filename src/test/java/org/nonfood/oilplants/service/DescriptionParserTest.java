package org.nonfood.oilplants.service;

import org.junit.jupiter.api.Test;
import org.nonfood.oilplants.model.TraitCriterion;
import static org.junit.jupiter.api.Assertions.*;

class DescriptionParserTest {
    private final DescriptionParser parser=new DescriptionParser();
    @Test void mapsEverydayDescriptionToSeveralStructuredTraits(){
        var result=parser.parse("叶子很细像针，树上长着棕色松塔。");
        assertEquals("tree",criterion(result,"plant_form").optionCode());
        assertEquals("needle",criterion(result,"leaf_shape").optionCode());
        assertEquals("brown",criterion(result,"fruit_color").optionCode());
        assertEquals("cone",criterion(result,"cone_shape").optionCode());
    }
    @Test void mapsSeasonalLeafColorAndFanShapeSeparately(){
        var result=parser.parse("秋天叶子会变成黄色，叶片像小扇子。");
        assertEquals("yellow",criterion(result,"autumn_leaf_color").optionCode());
        assertEquals("fan_shaped",criterion(result,"leaf_shape").optionCode());
        assertNull(find(result,"leaf_color"));
    }
    @Test void simpleNegationBecomesNegativeCriterion(){
        TraitCriterion criterion=criterion(parser.parse("这不是针形叶片"),"leaf_shape");
        assertTrue(criterion.negated());assertEquals("needle",criterion.optionCode());
    }
    @Test void colorKeywordsNeedPlantPartContext(){
        var fruit=parser.parse("果实是黄色");
        assertEquals("yellow",criterion(fruit,"fruit_color").optionCode());
        assertNull(find(fruit,"leaf_color"));
        assertFalse(parser.parse("黄色").hasRecognizedCriteria());
    }
    @Test void recognizesRoundFruitRoughBarkAndMountainHabitat(){
        var result=parser.parse("树皮粗糙，生长在山区，果实接近圆形");
        assertEquals("rough",criterion(result,"bark_texture").optionCode());
        assertEquals("mountain",criterion(result,"growth_habitat").optionCode());
        assertEquals("round",criterion(result,"fruit_shape").optionCode());
    }
    @Test void pineConeShapeWarningDoesNotClaimSpeciesIdentification(){
        var result=parser.parse("棕色松塔");
        assertEquals("cone",criterion(result,"cone_shape").optionCode());
        assertEquals("brown",criterion(result,"fruit_color").optionCode());
        assertTrue(result.warnings().stream().anyMatch(w->w.contains("不代表已经确认")));
    }
    @Test void unsupportedTermsDoNotInventCriteriaAndLongInputIsRejected(){
        assertFalse(parser.parse("一种奇特植物").hasRecognizedCriteria());
        assertThrows(IllegalArgumentException.class,()->parser.parse("a".repeat(501)));
    }
    private static TraitCriterion criterion(DescriptionParser.ParseResult result,String code){TraitCriterion c=find(result,code);assertNotNull(c,"missing trait "+code+" from "+result);return c;}
    private static TraitCriterion find(DescriptionParser.ParseResult result,String code){return result.criteria().stream().filter(c->c.traitCode().equals(code)).findFirst().orElse(null);}
}

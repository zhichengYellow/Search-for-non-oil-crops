package org.nonfood.oilplants.service;
import org.junit.jupiter.api.Test;import org.nonfood.oilplants.util.Validation;import java.math.BigDecimal;import static org.junit.jupiter.api.Assertions.*;
class ValidationTest {
 @Test void validatesUsernames(){assertTrue(Validation.username("Field_Research8"));assertFalse(Validation.username("8field"));assertFalse(Validation.username("ab"));assertFalse(Validation.username("bad-name"));}
 @Test void validatesPasswordStrengthAndBounds(){assertTrue(Validation.password("plantstudy8"));assertFalse(Validation.password("allletters"));assertFalse(Validation.password("abc123"));assertFalse(Validation.password("a".repeat(73)+"1"));}
 @Test void validatesNumericRanges(){assertTrue(Validation.percent(new BigDecimal("0")));assertTrue(Validation.percent(new BigDecimal("100")));assertFalse(Validation.percent(new BigDecimal("100.01")));assertTrue(Validation.range(new BigDecimal("2"),new BigDecimal("3")));assertFalse(Validation.range(new BigDecimal("4"),new BigDecimal("3")));}
}

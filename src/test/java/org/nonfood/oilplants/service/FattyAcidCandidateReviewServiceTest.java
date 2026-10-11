package org.nonfood.oilplants.service;

import org.junit.jupiter.api.Test;
import org.nonfood.oilplants.dao.FattyAcidCandidateDao;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FattyAcidCandidateReviewServiceTest {
    private final FattyAcidCandidateDao dao=mock(FattyAcidCandidateDao.class);
    private final FattyAcidCandidateReviewService service=new FattyAcidCandidateReviewService(dao);

    @Test void requiresExplicitPercentUnitAndEvidenceBeforeConfirmation(){
        assertThrows(IllegalArgumentException.class,()->service.review(1,"confirm","41.2","",2,
                "Source excerpt says value","Manually checked the cell",7));
        assertThrows(IllegalArgumentException.class,()->service.review(1,"confirm","41.2","%",0,
                "Source excerpt says value","Manually checked the cell",7));
        verifyNoInteractions(dao);
    }

    @Test void rejectsOutOfRangeAndExcessPrecisionBeforeDatabaseWrite(){
        assertThrows(IllegalArgumentException.class,()->service.review(1,"confirm","100.000001","%",2,
                "The source states percent unit","Checked row and column",7));
        assertThrows(IllegalArgumentException.class,()->service.review(1,"confirm","101","%",2,
                "The source states percent unit","Checked row and column",7));
        verifyNoInteractions(dao);
    }

    @Test void confirmationRequiresAConcreteSourceExcerptAndReason(){
        assertThrows(IllegalArgumentException.class,()->service.review(1,"confirm","12.34","%",2,
                "short","Checked row and column",7));
        assertThrows(IllegalArgumentException.class,()->service.review(1,"confirm","12.34","%",2,
                "The original source table provides a percent unit.","no",7));
        verifyNoInteractions(dao);
    }

    @Test void validConfirmationPassesReviewedValueAndSourceToDao() throws Exception {
        assertDoesNotThrow(()->service.review(9,"confirm","12.340","%",4,
                "The original source table states the component is percent.","I checked the row and matching column crop.",3));
        verify(dao).review(9,"confirm",new java.math.BigDecimal("12.340"),"%",4,
                "The original source table states the component is percent.","I checked the row and matching column crop.",3);
    }

    @Test void rejectionDoesNotRequireInventingAValueOrUnit() throws Exception {
        assertDoesNotThrow(()->service.review(9,"reject","","",0,"","Cell boundary cannot be read from scan.",3));
        verify(dao).review(9,"reject",null,null,0,null,"Cell boundary cannot be read from scan.",3);
    }
}

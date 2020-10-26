//CASE_VOID_DELETE
//if (_caseDisposition.dispositionType == "2100") 
//IR546286 Charlton added on 9/8/2020
//if (_caseDisposition.dispositionType == "2100") 
//IR546286 Charlton added on 10/24/2020 and changed 1301SC to 1300VSC per Liane Herbst SR via email on 10/23/2020
if (_caseDisposition.dispositionType == "2100" || _caseDisposition.dispositionType =="1300VSC" )
{
  cse = _caseDisposition.case;
  if (cse.dispositionType == "2100" || cse.dispositionType =="1300VSC") {
    cse.dispositionType = null;
    cse.dispositionDate = null;
    
    for (subCase in cse.collect("subCases[dispositionType == '2100' or dispositionType == '1300VSC']")) {
      subCase.dispositionType = null;
      subCase.dispositionDate = null;
    }    
    cse.saveOrUpdate();
  }
}













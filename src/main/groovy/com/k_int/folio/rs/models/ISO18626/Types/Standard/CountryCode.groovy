package com.k_int.folio.rs.models.ISO18626.Types.Standard

import com.k_int.folio.rs.models.ISO18626.ReferenceData;
import com.k_int.folio.rs.models.ISO18626.Types.ReferenceTypes;

/**
 * Standard: ISO 3166-1
 * List: https://datahub.io/core/country-list
 * 
 * @author Chas
 *
 */
public class CountryCode extends ReferenceData {

	public CountryCode(String code = null, boolean validated = false) {
		super(ReferenceTypes.COUNTRY_CODE, code, validated);
	}
}
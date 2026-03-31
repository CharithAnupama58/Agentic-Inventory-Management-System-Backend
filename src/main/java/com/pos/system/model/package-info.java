@FilterDef(
    name = "tenantFilter",
    parameters = @ParamDef(name = "tenantId", type = UUID.class)
)
package com.pos.system.model;

import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.ParamDef;
import java.util.UUID;
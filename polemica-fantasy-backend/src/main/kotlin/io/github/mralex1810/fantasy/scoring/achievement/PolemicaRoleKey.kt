package io.github.mralex1810.fantasy.scoring.achievement

import com.github.mafia.vyasma.polemica.library.model.game.Role

/** Same strings as `achievement_applicable_role.role` / V6 seed (`DON`, `MAFIA`, `PEACE`, `SHERIFF`). */
fun Role.toApplicableRoleKey(): String = name

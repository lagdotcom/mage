package mage.cards.d;

import mage.MageInt;
import mage.abilities.Ability;
import mage.abilities.common.EntersBattlefieldTriggeredAbility;
import mage.abilities.costs.common.SacrificeTargetCost;
import mage.abilities.effects.common.DamageTargetEffect;
import mage.abilities.effects.common.DoUnlessTargetPlayerOrTargetsControllerPaysEffect;
import mage.abilities.keyword.FlyingAbility;
import mage.cards.CardImpl;
import mage.cards.CardSetInfo;
import mage.constants.CardType;
import mage.constants.SubType;
import mage.filter.StaticFilters;
import mage.target.common.TargetOpponent;

import java.util.UUID;

/**
 * @author TheElk801
 */
public final class DemandingDragon extends CardImpl {

    public DemandingDragon(UUID ownerId, CardSetInfo setInfo) {
        super(ownerId, setInfo, new CardType[]{CardType.CREATURE}, "{3}{R}{R}");

        this.subtype.add(SubType.DRAGON);
        this.power = new MageInt(5);
        this.toughness = new MageInt(5);

        // Flying
        this.addAbility(FlyingAbility.getInstance());

        // When Demanding Dragon enters the battlefield, it deals 5 damage to target opponent unless that player sacrifices a creature.
        Ability ability = new EntersBattlefieldTriggeredAbility(new DoUnlessTargetPlayerOrTargetsControllerPaysEffect(
                new DamageTargetEffect(5),
                new SacrificeTargetCost(StaticFilters.FILTER_CONTROLLED_CREATURE_SHORT_TEXT)
        ).setText("it deals 5 damage to target opponent unless that player sacrifices a creature"));
        ability.addTarget(new TargetOpponent());
        this.addAbility(ability);
    }

    private DemandingDragon(final DemandingDragon card) {
        super(card);
    }

    @Override
    public DemandingDragon copy() {
        return new DemandingDragon(this);
    }
}

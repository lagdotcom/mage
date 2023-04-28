package mage.cards.u;

import java.util.UUID;
import mage.abilities.costs.mana.ManaCostsImpl;
import mage.abilities.decorator.ConditionalContinuousEffect;
import mage.ObjectColor;
import mage.abilities.Ability;
import mage.abilities.condition.Condition;
import mage.abilities.costs.Cost;
import mage.abilities.costs.OptionalAdditionalCost;
import mage.abilities.costs.OptionalAdditionalCostImpl;
import mage.abilities.costs.VariableCostType;
import mage.abilities.effects.common.PreventAllDamageByAllPermanentsEffect;
import mage.cards.CardImpl;
import mage.cards.CardSetInfo;
import mage.constants.CardType;
import mage.constants.Duration;
import mage.filter.FilterPermanent;
import mage.filter.predicate.Predicates;
import mage.filter.predicate.mageobject.ColorPredicate;
import mage.game.Game;

/**
 *
 * @author lagdotcom
 */
public final class Undergrowth extends CardImpl {
    private static final FilterPermanent filter = new FilterPermanent("nonred creature");

    static {
        filter.add(Predicates.not(new ColorPredicate(ObjectColor.RED)));
    }

    public Undergrowth(UUID ownerId, CardSetInfo setInfo) {
        super(ownerId, setInfo, new CardType[]{CardType.INSTANT}, "{G}");

        // As an additional cost to cast Undergrowth, you may pay {2}{R}.
        OptionalAdditionalCost cost = new OptionalAdditionalCostImpl("", "", "", new ManaCostsImpl<>("{2}{R}"));
        this.getSpellAbility().addCost(cost);
        cost.setCostType(VariableCostType.ADDITIONAL);

        // Prevent all combat damage that would be dealt this turn. If its additional cost was paid, Undergrowth doesn't affect combat damage that would be dealt by red creatures.
        this.getSpellAbility().addEffect(new ConditionalContinuousEffect(
                new PreventAllDamageByAllPermanentsEffect(filter, Duration.EndOfTurn, true),
                new PreventAllDamageByAllPermanentsEffect(Duration.EndOfTurn, true),
                OptionalAdditionalCostPaidCondition.instance,
                "Prevent all combat damage that would be dealt this turn. If its additional cost was paid, Undergrowth doesn't affect combat damage that would be dealt by red creatures."));
    }

    private Undergrowth(final Undergrowth card) {
        super(card);
    }

    @Override
    public Undergrowth copy() {
        return new Undergrowth(this);
    }
}

enum OptionalAdditionalCostPaidCondition implements Condition {
    instance;

    @Override
    public boolean apply(Game game, Ability source) {
        for (Cost cost : source.getCosts()) {
            if (cost instanceof OptionalAdditionalCost) {
                if (((OptionalAdditionalCost) cost).isActivated()) {
                    return true;
                }
            }
        }

        return false;
    }
}

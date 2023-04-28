package mage.cards.u;

import java.util.Iterator;
import java.util.UUID;
import mage.abilities.costs.mana.ManaCostsImpl;
import mage.abilities.decorator.ConditionalPreventionEffect;
import mage.MageObject;
import mage.ObjectColor;
import mage.abilities.Ability;
import mage.abilities.SpellAbility;
import mage.abilities.StaticAbility;
import mage.abilities.condition.Condition;
import mage.abilities.costs.Costs;
import mage.abilities.costs.OptionalAdditionalCost;
import mage.abilities.costs.OptionalAdditionalCostImpl;
import mage.abilities.costs.OptionalAdditionalSourceCosts;
import mage.abilities.costs.VariableCostType;
import mage.abilities.effects.common.PreventAllDamageByAllPermanentsEffect;
import mage.cards.Card;
import mage.cards.CardImpl;
import mage.cards.CardSetInfo;
import mage.constants.CardType;
import mage.constants.Duration;
import mage.constants.Outcome;
import mage.constants.Zone;
import mage.filter.FilterPermanent;
import mage.filter.predicate.Predicates;
import mage.filter.predicate.mageobject.ColorPredicate;
import mage.game.Game;
import mage.players.Player;

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
        this.addAbility(new OptionalAdditionalCostAbility("{2}{R}", "As an additional cost to cast Undergrowth, you may pay {2}{R}.")
            .setRuleAtTheTop(true));

        // Prevent all combat damage that would be dealt this turn. If its additional cost was paid, Undergrowth doesn't affect combat damage that would be dealt by red creatures.
        this.getSpellAbility().addEffect(new ConditionalPreventionEffect(
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

class OptionalAdditionalCostAbility extends StaticAbility implements OptionalAdditionalSourceCosts {
    String manaString;
    OptionalAdditionalCost manaCost;
    String staticText;

    public OptionalAdditionalCostAbility(String manaString, String text) {
        super(Zone.STACK, null);
        this.staticText = text;
        this.manaString = manaString;
        this.manaCost = new OptionalAdditionalCostImpl(manaString, "", "", new ManaCostsImpl<>(manaString));
        this.manaCost.setCostType(VariableCostType.ADDITIONAL);
    }

    @Override
    public OptionalAdditionalCostAbility copy() {
        return new OptionalAdditionalCostAbility(this.manaString, this.staticText);
    }

    void setActivated(boolean active) {
        this.activated = active;

        if (active) {
            this.manaCost.activate();
        } else {
            this.manaCost.reset();
        }
    }

    @Override
    public void addOptionalAdditionalCosts(Ability ability, Game game) {
        if (!(ability instanceof SpellAbility)) {
            return;
        }
        Player player = game.getPlayer(ability.getControllerId());
        if (player == null) {
            return;
        }
        this.setActivated(false);

        // TODO: add AI support for not fogging your own creatures
        if (manaCost.canPay(ability, this, ability.getControllerId(), game) && player.chooseUse(
                /* Outcome.Benefit */Outcome.AIDontUseIt, "Pay " + manaCost.getText(true) + "?", ability, game)) {

            for (Iterator it = ((Costs) manaCost).iterator(); it.hasNext();) {
                ManaCostsImpl cost = (ManaCostsImpl) it.next();
                ability.getManaCostsToPay().add(cost.copy());
                this.setActivated(true);
            }
        }
    }

    @Override
    public String getRule() {
        return this.staticText;
    }

    @Override
    public String getCastMessageSuffix() {
        return manaCost.getCastSuffixMessage(0);
    }
}

enum OptionalAdditionalCostPaidCondition implements Condition {
    instance;

    @Override
    public boolean apply(Game game, Ability abilityToCheck) {
        MageObject source = abilityToCheck.getSourceObject(game);
        if (source instanceof Card) {
            for (Ability ability : ((Card) source).getAbilities(game)) {
                if (ability instanceof OptionalAdditionalCostAbility) {
                    return ((OptionalAdditionalCostAbility) ability).isActivated();
                }
            }
        }

        return false;
    }
}

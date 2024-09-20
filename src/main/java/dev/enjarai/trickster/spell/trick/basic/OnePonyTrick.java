package dev.enjarai.trickster.spell.trick.basic;

import dev.enjarai.trickster.spell.Fragment;
import dev.enjarai.trickster.spell.Pattern;
import dev.enjarai.trickster.spell.SpellContext;
import dev.enjarai.trickster.spell.fragment.NumberFragment;
import dev.enjarai.trickster.spell.trick.Trick;
import dev.enjarai.trickster.spell.trick.blunder.BlunderException;

import java.util.List;

/**
 * Continues to exist for backwards compatibility, users should instead use the ConstantRevision with the same pattern.
 */
public class OnePonyTrick extends Trick {
    public OnePonyTrick() {
        super(Pattern.of(7, 4, 1));
    }

    @Override
    public Fragment activate(SpellContext ctx, List<Fragment> fragments) throws BlunderException {
        return new NumberFragment(2);
    }
}

/* This file was generated by SableCC (http://www.sablecc.org/). */

package org.eclipse.texlipse.bibparser.node;

import org.eclipse.texlipse.bibparser.analysis.*;

@SuppressWarnings("nls")
public final class TPreamble extends Token
{
    public TPreamble(String text)
    {
        setText(text);
    }

    public TPreamble(String text, int line, int pos)
    {
        setText(text);
        setLine(line);
        setPos(pos);
    }

    @Override
    public Object clone()
    {
      return new TPreamble(getText(), getLine(), getPos());
    }

    public void apply(Switch sw)
    {
        ((Analysis) sw).caseTPreamble(this);
    }
}

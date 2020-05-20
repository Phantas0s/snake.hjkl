#!/bin/bash
# This is the fanciest way to deploy you'll ever see

boot prod
rsync -arvz target/ prod:/usr/share/nginx/html/matthieucneude/snake/

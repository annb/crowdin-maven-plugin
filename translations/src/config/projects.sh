#
# Copyright (C) 2003-2013 eXo Platform SAS.
#
# This is free software; you can redistribute it and/or modify it
# under the terms of the GNU Lesser General Public License as
# published by the Free Software Foundation; either version 3 of
# the License, or (at your option) any later version.
#
# This software is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this software; if not, write to the Free
# Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
# 02110-1301 USA, or see the FSF site: http://www.fsf.org.
#

projects=( 'platform' 'wiki'  )
versions=( '4.0.x' '4.0.x' )

echo "WORKING PROJECTS = [${projects[@]}]"

# Commit message "PLF-XXXX: inject en translation W29"
plf_langs=( 'en' 'fr' 'vi' 'sv_SE' 'ja' 'es_ES' )
plf_issue='PLF-9999:'
plf_week='W30'


#!/bin/bash
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

# Purpose : Restore projects structure

# import projects declaration
source "../src/config/projects.sh"

# eXoProjects directory 
EXO_PROJECTS=`pwd`

length=${#projects[@]}
length_langs=${#plf_langs[@]}

echo "=========================Restoring projects structure========================="
echo ""

for (( i=0;i<$length;i++)); do
  if [ -d $EXO_PROJECTS/${projects[${i}]}-${versions[${i}]} ]; then
    mv ${projects[${i}]}-${versions[${i}]} ${projects[${i}]}
    rm -f ${projects[${i}]}/temp.patch
    echo "Renamed ${projects[${i}]}-${versions[${i}]} to ${projects[${i}]}"
  fi
done

echo "=========================Pushing to github========================="
echo ""



echo ""
##for each project 
for (( i=0;i<$length;i++)); do
  if [ -d $EXO_PROJECTS/${projects[${i}]} ]; then
	cd $EXO_PROJECTS/${projects[${i}]}
	echo "For ${projects[${i}]}"
	git checkout stable/${versions[${i}]}
	git remote rm exodev
	git remote add exodev https://github.com/annb/${projects[${i}]}.git       
	git fetch exodev


	#for each language
	for ((j=0;j<$length_langs;j++)); do
			echo "Language: ${plf_langsFull[${j}]} (${plf_langs[${j}]})"
			FILTER_LANGXML="${plf_langs[${j}]}.xml"
			FILTER_LANGPROPERTIES="${plf_langs[${j}]}.properties"
			NUMBER_XML=$(git status --porcelain | grep $FILTER_LANGXML | wc -l)
			NUMBER_PROPERTIES=$(git status --porcelain | grep $FILTER_LANGPROPERTIES | wc -l)
			echo "number xml: " $NUMBER_XML  " number properties: " $NUMBER_PROPERTIES

			if [ $(($NUMBER_XML + $NUMBER_PROPERTIES)) -ne 0 ]; then 
				MESSAGE_COMMIT="${plf_issue[${j}]}: [crowdin-plugin] inject ${plf_langsFull[${j}]} (${plf_langs[${j}]}) translation $plf_week"
				echo "Message commit: $MESSAGE_COMMIT "
				echo "There are some changes"; 		
				
				git branch -D feature/${versions[${i}]}-translation
				## Commit message "PLF-XXXX: inject en,fr translation W29"
				git status --porcelain | grep $FILTER_LANGXML | cut -c 4- | xargs git add
				git status --porcelain | grep $FILTER_LANGPROPERTIES | cut -c 4- | xargs git add
				git commit -m "$MESSAGE_COMMIT"				
				git checkout -b feature/${versions[${i}]}-translation remotes/exodev/feature/${versions[${i}]}-translation
				git cherry-pick HEAD@{1}
				echo "Push to feature/${versions[${i}]}-translation"
				#git push exodev feature/${versions[${i}]}-translation
				git checkout stable/${versions[${i}]}					
									
			else 
			  echo "no changes, no commit ";
			fi
		echo "-------------------"
		echo ""
	done
  fi
done


echo ""
echo "=========================Finished=============================================="

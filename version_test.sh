#!/bin/bash

myvar=$(echo $1)

if [[ -z $myvar ]]; then

	pkg_version='0.1'

else

	pkg_pat_version=$(echo $1 | cut -d '.' -f 3)
	pkg_min_version=$(echo $1 | cut -d '.' -f 2)
	pkg_maj_version=$(echo $1 | cut -d '.' -f 1)

	if [[ -z $pkg_maj_version ]] || [[ $pkg_maj_version -eq '' ]]; then
		pkg_maj_version=0
                pkg_min_version=$(expr $pkg_min_version + 1)
 		pkg_version=$pkg_maj_version.$pkg_min_version.$pkg_pat_version
	elif [[ $pkg_pat_version == 9 ]]; then
		pkg_pat_version=0
		pkg_min_version=$(expr $pkg_min_version + 1)
		pkg_version=$pkg_maj_version.$pkg_min_version.$pkg_pat_version
	elif [[ $pkg_min_version == 9 ]]; then
		pkg_maj_version=$(expr $pkg_maj_version + 1)
		pkg_min_version=0
		pkg_version=$pkg_maj_version.$pkg_min_version.$pkg_pat_version
	else
		pkg_pat_version=$(expr $pkg_pat_version + 1)
		pkg_version=$pkg_maj_version.$pkg_min_version.$pkg_pat_version
	fi
fi

echo "PACKAGE VERSION: $pkg_version"
